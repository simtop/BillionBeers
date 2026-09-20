package com.simtop.billionbeers.desktop

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DesktopDataIntegrationTest {

  private lateinit var server: MockWebServer
  private lateinit var databaseDirectory: java.nio.file.Path

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    databaseDirectory = Files.createTempDirectory("billionbeers-desktop")
  }

  @After
  fun tearDown() {
    server.shutdown()
    databaseDirectory.toFile().deleteRecursively()
  }

  @Test
  fun `real data graph persists edits and paging across close and reopen`() = runBlocking {
    enqueueCatalogPage("1", "2", totalCount = 75)
    enqueueCatalogPage("3", "4", totalCount = 75)
    val databasePath = databaseDirectory.resolve("beers.db").toString()
    var runtime: DesktopDataRuntime? = null

    try {
      runtime = openRuntime(databasePath)
      val pager = runtime.pagerFactory.create()
      pager.loadFirstPage()
      pager.loadNextPage()

      assertEquals(listOf("1", "2", "3", "4"), runtime.repository.getAllBeersFromDB().map(Beer::id))
      assertEquals(3, runtime.repository.pagingNextKey("catalog:en"))
      assertEquals(4, runtime.repository.observeBeers().first().size)

      val first = runtime.repository.getBeerById("1")!!
      val second = runtime.repository.getBeerById("2")!!
      runtime.repository.updateFavorite(first.copy(isFavorite = true))
      runtime.repository.updateFavorite(second.copy(isFavorite = true))
      assertEquals(
        listOf("1", "2"),
        runtime.repository.observeFavoriteBeers().first().map(Beer::id),
      )
      runtime.repository.updateFavorite(second.copy(isFavorite = false))
      runtime.repository.updateAvailability(second.copy(availability = false))

      assertEquals(listOf("1"), runtime.repository.observeFavoriteBeers().first().map(Beer::id))
      assertFalse(runtime.repository.getBeerById("2")!!.availability)
    } finally {
      runtime?.close()
    }

    val offlineRuntime = openRuntime(databasePath, "http://127.0.0.1:1/")
    try {
      assertEquals(
        listOf("1", "2", "3", "4"),
        offlineRuntime.repository.getAllBeersFromDB().map(Beer::id),
      )
      assertTrue(offlineRuntime.repository.getBeerById("1")!!.isFavorite)
      assertFalse(offlineRuntime.repository.getBeerById("2")!!.availability)
      assertEquals(3, offlineRuntime.repository.pagingNextKey("catalog:en"))
    } finally {
      offlineRuntime.close()
    }

    enqueueCatalogPage("1", "2", totalCount = 75, firstName = "Refreshed")
    val refreshedRuntime = openRuntime(databasePath)
    try {
      val pager = refreshedRuntime.pagerFactory.create()
      pager.loadFirstPage()

      assertEquals("Refreshed", refreshedRuntime.repository.getBeerById("1")!!.name)
      assertTrue(refreshedRuntime.repository.getBeerById("1")!!.isFavorite)
      assertFalse(refreshedRuntime.repository.getBeerById("2")!!.availability)
      assertEquals(3, refreshedRuntime.repository.pagingNextKey("catalog:en"))
    } finally {
      refreshedRuntime.close()
    }

    assertEquals(3, server.requestCount)
  }

  @Test
  fun `real query pagers stay independent and do not persist catalog state`() = runBlocking {
    enqueueQueryPage("search-a", "10")
    enqueueQueryPage("search-b", "20")
    val runtime = openRuntime(databaseDirectory.resolve("queries.db").toString())

    try {
      val first = runtime.pagerFactory.create(BeersQuery(search = "search-a"))
      val second = runtime.pagerFactory.create(BeersQuery(search = "search-b"))
      first.loadFirstPage()
      second.loadFirstPage()

      assertEquals(listOf("10"), first.data.first().map(Beer::id))
      assertEquals(listOf("20"), second.data.first().map(Beer::id))
      assertEquals(0, runtime.repository.countDBEntries())
      assertEquals(null, runtime.repository.pagingNextKey("catalog:en"))
    } finally {
      runtime.close()
    }
  }

  private fun openRuntime(databasePath: String, baseUrl: String = server.url("/").toString()) =
    DesktopDataRuntime.open(
      DesktopDataConfig(
        databasePath = databasePath,
        apiBaseUrl = baseUrl,
      )
    )

  private fun enqueueCatalogPage(
    firstId: String,
    secondId: String,
    totalCount: Int,
    firstName: String = "Beer $firstId",
  ) {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .addHeader("X-Total-Count", totalCount)
        .setBody("[${beerJson(firstId, firstName)},${beerJson(secondId, "Beer $secondId")}]")
    )
  }

  private fun enqueueQueryPage(search: String, id: String) {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .addHeader("X-Total-Count", 1)
        .setBody("[${beerJson(id, search)}]")
    )
  }

  private fun beerJson(id: String, name: String) =
    "{\"id\":\"$id\",\"name\":\"$name\",\"abv\":5.0,\"ibu\":20.0," +
      "\"translations\":[],\"foodPairing\":[]}"
}
