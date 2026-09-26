package com.simtop.billionbeers.iosshared

import com.simtop.navigation.contract.PortableRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class IosRouteRequestBufferTest {
  @Test
  fun routeSentBeforeCollectionIsRetained() = runTest {
    val buffer = IosRouteRequestBuffer()

    assertTrue(buffer.trySend(PortableRoute.Favorites))
    assertEquals(PortableRoute.Favorites, buffer.routes.take(1).toList().single())

    buffer.close()
  }

  @Test
  fun routesAreDeliveredInOrder() = runTest {
    val buffer = IosRouteRequestBuffer()
    val expected =
      listOf(
        PortableRoute.BeersList,
        PortableRoute.Favorites,
        PortableRoute.BeersSearch,
      )
    val collected = mutableListOf<PortableRoute>()
    val collector = launch { buffer.routes.take(expected.size).toList(collected) }

    expected.forEach { route -> assertTrue(buffer.trySend(route)) }
    collector.join()
    buffer.close()

    assertTrue(expected == collected)
  }

  @Test
  fun closeIsIdempotentAndRejectsLateRoutes() = runTest {
    val buffer = IosRouteRequestBuffer()

    buffer.close()
    buffer.close()

    assertFalse(buffer.trySend(PortableRoute.BeersList))
  }
}
