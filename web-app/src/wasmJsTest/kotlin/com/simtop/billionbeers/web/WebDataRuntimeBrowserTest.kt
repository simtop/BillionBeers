package com.simtop.billionbeers.web

import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.core.core.PagingState
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class WebDataRuntimeBrowserTest {

  @Test
  fun browserImageFetchReturnsEncodedBytes() = runTest {
    withBrowserFetchFixture {
      val runtime = WebDataRuntime.open(WebDataConfig(apiBaseUrl = "https://fixture.example/"))
      try {
        val bytes = runtime.loadImage("https://fixture.example/fixture-1.png")
        assertNotNull(bytes)
        assertEquals(true, bytes.isNotEmpty())
        assertEquals("https://fixture.example/fixture-1.png", browserFixtureLastRequestUrl())
      } finally {
        runtime.close()
      }
    }
  }

  @Test
  fun configuredImageProxyReceivesEncodedOriginalUrl() = runTest {
    withBrowserFetchFixture {
      val runtime =
        WebDataRuntime.open(
          WebDataConfig(
            apiBaseUrl = "https://fixture.example/",
            imageProxyBaseUrl = "https://proxy.example/image",
          )
        )
      try {
        assertNotNull(runtime.loadImage("https://dropgate.malvik.dev/brewbuddy/images/fixture.jpg"))
        val requestUrl = browserFixtureLastRequestUrl()
        assertEquals("https://proxy.example/image", requestUrl.substringBefore("?"))
        assertContains(
          requestUrl,
          "url=https%3A%2F%2Fdropgate.malvik.dev%2Fbrewbuddy%2Fimages%2Ffixture.jpg",
        )
      } finally {
        runtime.close()
      }
    }
  }

  @Test
  fun browserFetchFeedsTheCommonRepository() = runTest {
    withBrowserFetchFixture {
      val runtime = WebDataRuntime.open(WebDataConfig(apiBaseUrl = "https://fixture.example/"))
      try {
        val page = runtime.repository.getBeersPageFromApi(1)
        assertEquals(listOf("fixture-1"), page.items.map { it.id })
        assertEquals(40, page.totalCount)
      } finally {
        runtime.close()
      }
    }
  }

  @Test
  fun browserClientDoesNotRetryApplicationRequests() = runTest {
    withBrowserFetchFixture {
      val runtime = WebDataRuntime.open(WebDataConfig(apiBaseUrl = "https://fixture.example/"))
      try {
        val pager = runtime.pagerFactory.create(BeersQuery(search = "no-retry"))
        pager.loadFirstPage()
        assertEquals(1, browserFixtureRequestCount())
        assertEquals(true, pager.pagingState.value is PagingState.Error)
      } finally {
        runtime.close()
      }
    }
  }

  @Test
  fun cancellingBrowserFetchCancelsTheRepositoryOperation() = runTest {
    withBrowserFetchFixture {
      val runtime = WebDataRuntime.open(WebDataConfig(apiBaseUrl = "https://fixture.example/"))
      try {
        val job = launch {
          runtime.repository.getBeersPageFromApi(
            page = 1,
            query = BeersQuery(search = "cancel"),
          )
        }
        delay(50)
        job.cancel()
        job.join()
        assertEquals(true, job.isCancelled)
      } finally {
        runtime.close()
      }
    }
  }

  @Test
  fun missingTotalHeaderUsesEmptyPageTermination() = runTest {
    withBrowserFetchFixture {
      val runtime = WebDataRuntime.open(WebDataConfig(apiBaseUrl = "https://fixture.example/"))
      try {
        val pager = runtime.pagerFactory.create(BeersQuery(search = "no-header"))
        pager.loadFirstPage()
        pager.loadNextPage()
        pager.loadNextPage()
        assertEquals(listOf("fixture-1", "fixture-2"), pager.data.first().map { it.id })
        assertEquals(PagingState.EndOfPagination(null), pager.pagingState.value)
      } finally {
        runtime.close()
      }
    }
  }

  @Test
  fun browserHttpAndSerializationFailuresBecomeTypedPagerErrors() = runTest {
    withBrowserFetchFixture {
      val runtime = WebDataRuntime.open(WebDataConfig(apiBaseUrl = "https://fixture.example/"))
      try {
        val httpPager = runtime.pagerFactory.create(BeersQuery(search = "http-error"))
        httpPager.loadFirstPage()
        assertEquals(PagingState.Error(FetchBeersError.NotFound, true), httpPager.pagingState.value)

        val malformedPager = runtime.pagerFactory.create(BeersQuery(search = "malformed"))
        malformedPager.loadFirstPage()
        val state = malformedPager.pagingState.value
        assertEquals(true, state is PagingState.Error && state.isFirstPage)
      } finally {
        runtime.close()
      }
    }
  }

  @Test
  fun catalogPagerLoadsTwoPagesAndPreservesLocalFavoriteOnRefresh() = runTest {
    withBrowserFetchFixture {
      val runtime = WebDataRuntime.open(WebDataConfig(apiBaseUrl = "https://fixture.example/"))
      try {
        runtime.storage.deleteAll()
        val pager = runtime.pagerFactory.create()
        pager.loadFirstPage()
        pager.loadNextPage()
        assertEquals(listOf("fixture-1", "fixture-2"), pager.data.first().map { it.id })

        val favorite = pager.data.first().first().copy(isFavorite = true)
        assertEquals(
          com.simtop.core.core.Either.Right(Unit),
          runtime.repository.updateFavorite(favorite),
        )
        pager.loadFirstPage()
        assertEquals(true, pager.data.first().first { it.id == "fixture-1" }.isFavorite)

        val searchPager = runtime.pagerFactory.create(BeersQuery(search = "stout"))
        searchPager.loadFirstPage()
        assertEquals(listOf("fixture-1"), searchPager.data.first().map { it.id })
        assertEquals(2, runtime.repository.countDBEntries())
      } finally {
        runtime.storage.deleteAll()
        runtime.close()
      }
    }
  }

  @Test
  fun committedRepositoryRowsSurviveRuntimeCloseAndReopen() = runTest {
    val first = WebDataRuntime.open(WebDataConfig(apiBaseUrl = "https://fixture.example/"))
    val beer =
      Beer(
        id = "web-1",
        name = "Browser Lager",
        tagline = "Fixture",
        description = "Repository",
        imageUrl = "https://fixture.example/web-1.png",
        abv = 4.8,
        ibu = 20.0,
        foodPairing = listOf("chips"),
        availability = false,
        isFavorite = true,
      )

    first.repository.insertAllToDB(listOf(beer))
    assertEquals(listOf(beer), first.repository.getAllBeersFromDB())
    first.close()

    val reopened = WebDataRuntime.open(WebDataConfig(apiBaseUrl = "https://fixture.example/"))
    assertEquals(listOf(beer), reopened.repository.getAllBeersFromDB())
    reopened.storage.deleteAll()
    reopened.close()
  }
}
