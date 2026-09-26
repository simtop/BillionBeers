package com.simtop.billionbeers.shared.app

import androidx.compose.ui.unit.dp
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.fakes.FakeBeersPagerFactory
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.core.core.DefaultCoroutineDispatcherProvider
import com.simtop.navigation.contract.PortableRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedAppLayoutPolicyTest {

  @Test
  fun classifiesAvailableWidthIntoStableShellBands() {
    assertEquals(SharedAppWidthClass.Compact, classifySharedAppWidth(599.dp))
    assertEquals(SharedAppWidthClass.Medium, classifySharedAppWidth(600.dp))
    assertEquals(SharedAppWidthClass.Medium, classifySharedAppWidth(839.dp))
    assertEquals(SharedAppWidthClass.Expanded, classifySharedAppWidth(840.dp))
  }

  @Test
  fun expandsOnlyForCatalogRootWithDetailStack() {
    val navigation = navigation()
    val list = navigation.current

    assertFalse(shouldShowExpandedCatalogDetail(1000.dp, navigation.entries))

    navigation.navigate(PortableRoute.BeerDetail(Beer.empty.copy(id = "beer-1")))
    assertTrue(shouldShowExpandedCatalogDetail(1000.dp, navigation.entries))
    assertFalse(shouldShowExpandedCatalogDetail(700.dp, navigation.entries))

    navigation.navigate(PortableRoute.BeersSearch)
    assertFalse(shouldShowExpandedCatalogDetail(1000.dp, navigation.entries))
    assertFalse(list.isClosed)
  }

  private fun navigation(): SharedAppNavigationState {
    val repository = FakeBeersRepository()
    return SharedAppNavigationState(
      repository = repository,
      pagerFactory = FakeBeersPagerFactory(repository),
      coroutineDispatcher = DefaultCoroutineDispatcherProvider(),
      initialRoute = PortableRoute.BeersList,
    )
  }
}
