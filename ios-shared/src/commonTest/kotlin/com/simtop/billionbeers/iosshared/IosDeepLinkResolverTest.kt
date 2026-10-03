package com.simtop.billionbeers.iosshared

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.navigation.contract.DeepLinkParser
import com.simtop.navigation.contract.PortableRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

class IosDeepLinkResolverTest {

  @Test
  fun rootLinksResolveWithoutLookingUpABeer() = runTest {
    val fake = FakeBeersRepository()
    val lookups = mutableListOf<String>()
    val repository = recordingRepository(fake, lookups)

    assertEquals(
      PortableRoute.BeersList,
      resolveIosDeepLink(DeepLinkParser.SCHEME, DeepLinkParser.HOST_BEERS, emptyList(), repository),
    )
    assertEquals(
      PortableRoute.Favorites,
      resolveIosDeepLink(DeepLinkParser.SCHEME, DeepLinkParser.HOST_FAVORITES, emptyList(), repository),
    )
    assertEquals(emptyList(), lookups)
    assertEquals(emptyList(), fake.apiRequests)
  }

  @Test
  fun detailLinkRetainsTheCompleteLocallyCachedBeer() = runTest {
    val beer = Beer.empty.copy(id = "42", name = "Cached IPA", isFavorite = true)
    val fake = FakeBeersRepository(listOf(Beer.empty.copy(id = "other"), beer))
    val lookups = mutableListOf<String>()

    assertEquals(
      PortableRoute.BeerDetail(beer),
      resolveIosDeepLink(
        DeepLinkParser.SCHEME,
        DeepLinkParser.HOST_BEERS,
        listOf("42"),
        recordingRepository(fake, lookups),
      ),
    )
    assertEquals(listOf("42"), lookups)
    assertEquals(emptyList(), fake.apiRequests)
  }

  @Test
  fun missingCachedBeerDoesNotFallBackToTheNetwork() = runTest {
    val fake = FakeBeersRepository(listOf(Beer.empty.copy(id = "other")))
    val lookups = mutableListOf<String>()

    assertNull(
      resolveIosDeepLink(
        DeepLinkParser.SCHEME,
        DeepLinkParser.HOST_BEERS,
        listOf("42"),
        recordingRepository(fake, lookups),
      )
    )
    assertEquals(listOf("42"), lookups)
    assertEquals(emptyList(), fake.apiRequests)
  }

  @Test
  fun malformedLinksDoNotAccessTheRepository() = runTest {
    val fake = FakeBeersRepository()
    val lookups = mutableListOf<String>()
    val repository = recordingRepository(fake, lookups)
    val links =
      listOf(
        Triple(null, DeepLinkParser.HOST_BEERS, listOf("42")),
        Triple("https", DeepLinkParser.HOST_BEERS, listOf("42")),
        Triple(DeepLinkParser.SCHEME, null, listOf("42")),
        Triple(DeepLinkParser.SCHEME, "unknown", listOf("42")),
        Triple(DeepLinkParser.SCHEME, DeepLinkParser.HOST_FAVORITES, listOf("extra")),
      )

    links.forEach { (scheme, host, segments) ->
      assertNull(resolveIosDeepLink(scheme, host, segments, repository))
    }
    assertEquals(emptyList(), lookups)
    assertEquals(emptyList(), fake.apiRequests)
  }

  @Test
  fun cancelledCacheLookupPropagatesCancellation() = runTest {
    val cancellation = CancellationException("Host disposed")
    val repository =
      object : BeersRepository by FakeBeersRepository() {
        override suspend fun getBeerById(id: String): Beer? = throw cancellation
      }

    val result = runCatching {
      resolveIosDeepLink(DeepLinkParser.SCHEME, DeepLinkParser.HOST_BEERS, listOf("42"), repository)
    }

    assertEquals(cancellation, result.exceptionOrNull())
  }

  private fun recordingRepository(
    fake: FakeBeersRepository,
    lookups: MutableList<String>,
  ): BeersRepository =
    object : BeersRepository by fake {
      override suspend fun getBeerById(id: String): Beer? {
        lookups += id
        return fake.getBeerById(id)
      }
    }
}
