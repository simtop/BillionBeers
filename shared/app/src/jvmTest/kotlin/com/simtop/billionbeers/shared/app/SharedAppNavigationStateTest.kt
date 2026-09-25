package com.simtop.billionbeers.shared.app

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.fakes.FakeBeersPagerFactory
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.core.core.DefaultCoroutineDispatcherProvider
import com.simtop.navigation.contract.BrowseCategory
import com.simtop.navigation.contract.PortableRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SharedAppNavigationStateTest {

  private val beer = Beer.empty.copy(id = "beer-1", name = "Test Lager")

  @Test
  fun coveredSearchEntryRetainsIdentityAndPoppedDetailsClose() {
    val navigation = navigation()
    val list = navigation.current

    navigation.navigate(PortableRoute.BeersSearch)
    val search = navigation.current as SearchEntry
    search.viewModel.onQueryChange("lager")
    val retainedListState = search.listState
    navigation.navigate(PortableRoute.BeerDetail(beer))
    val firstDetail = navigation.current
    navigation.navigate(PortableRoute.BeerDetail(beer))
    val secondDetail = navigation.current

    assertNotEquals(firstDetail.id, secondDetail.id)
    assertSame(search, navigation.entries[1])
    assertEquals("lager", search.viewModel.query.value)
    assertSame(retainedListState, search.listState)
    assertFalse(search.isClosed)

    assertTrue(navigation.pop())
    assertTrue(secondDetail.isClosed)
    assertSame(firstDetail, navigation.current)
    assertTrue(navigation.pop())
    assertTrue(firstDetail.isClosed)
    assertSame(search, navigation.current)
    assertFalse(search.isClosed)
    assertEquals(list.id, navigation.entries.first().id)
  }

  @Test
  fun browseBackPopsCategoryBeforeHome() {
    val navigation = navigation()
    navigation.navigate(PortableRoute.BeerBrowse)
    val home = navigation.current as BrowseHomeEntry
    home.selectedTab = 1
    navigation.selectBrowse(BrowseSelection(styleId = "style", name = "Lager"))
    val category = navigation.current as BrowseBeersEntry
    navigation.navigate(PortableRoute.BeerDetail(beer))
    val detail = navigation.current

    assertTrue(navigation.pop())
    assertTrue(detail.isClosed)
    assertSame(category, navigation.current)
    assertFalse(category.isClosed)
    assertTrue(navigation.pop())
    assertTrue(category.isClosed)
    assertSame(home, navigation.current)
    assertEquals(1, home.selectedTab)
    assertFalse(home.isClosed)
    assertTrue(navigation.pop())
    assertTrue(home.isClosed)
  }

  @Test
  fun externalCategoryRouteRestoresRetainedCategoryEntry() {
    val navigation = navigation()
    navigation.navigate(PortableRoute.BeerBrowse)
    navigation.selectBrowse(BrowseSelection(styleId = "style", name = "Lager"))
    val category = navigation.current
    navigation.navigate(PortableRoute.BeerDetail(beer))

    navigation.replaceFromRoute(
      PortableRoute.BeerBrowseSelection(BrowseCategory.Style("style", "Lager"))
    )

    assertSame(category, navigation.current)
    assertTrue(navigation.entries.none { it is DetailEntry })
    assertFalse(category.isClosed)
  }

  @Test
  fun switchingRootsRetainsOnlyBoundedRootOwners() {
    val navigation = navigation()
    val list = navigation.current
    navigation.navigate(PortableRoute.BeersSearch)
    val search = navigation.current

    navigation.navigate(PortableRoute.Favorites)
    val favorites = navigation.current
    assertTrue(search.isClosed)
    assertFalse(list.isClosed)

    navigation.navigate(PortableRoute.BeersList)
    assertSame(list, navigation.current)
    assertFalse(list.isClosed)
    assertFalse(favorites.isClosed)
    assertEquals(1, navigation.entries.size)

    navigation.disposeAll()
    assertTrue(list.isClosed)
    assertTrue(favorites.isClosed)
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
