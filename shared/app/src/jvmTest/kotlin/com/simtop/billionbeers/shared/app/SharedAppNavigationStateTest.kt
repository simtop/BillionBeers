package com.simtop.billionbeers.shared.app

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.beerdomain.fakes.FakeBeersPagerFactory
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.billionbeers.testing_utils.MainDispatcherExtension
import com.simtop.core.core.DefaultCoroutineDispatcherProvider
import com.simtop.navigation.contract.BrowseCategory
import com.simtop.navigation.contract.PortableRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.RegisterExtension

class SharedAppNavigationStateTest {

  @JvmField @RegisterExtension val mainDispatcher = MainDispatcherExtension()

  private val beer = Beer.empty.copy(id = "beer-1", name = "Test Lager")

  private val categoryRoutesAndQueries =
    listOf(
      PortableRoute.BeerBrowseSelection(BrowseCategory.Style("style-1", "Lager")) to
        BeersQuery(styleId = "style-1"),
      PortableRoute.BeerBrowseSelection(BrowseCategory.Brewery("brewery-1", "Brewery")) to
        BeersQuery(breweryId = "brewery-1"),
    )

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
  fun catalogEntryAndListStateSurviveDetailSelections() {
    val navigation = navigation()
    val catalog = navigation.current as ListEntry
    val listState = catalog.listState

    navigation.navigate(PortableRoute.BeerDetail(beer))
    val firstDetail = navigation.current
    navigation.navigate(PortableRoute.BeerDetail(beer.copy(id = "beer-2")))
    val secondDetail = navigation.current

    assertSame(catalog, navigation.entries.first())
    assertSame(listState, catalog.listState)
    assertSame(secondDetail, navigation.current)
    assertTrue(navigation.entries.drop(1).all { it is DetailEntry })

    assertTrue(navigation.pop())
    assertSame(firstDetail, navigation.current)
    assertSame(catalog, navigation.entries.first())
    assertTrue(navigation.pop())
    assertSame(catalog, navigation.current)
    assertFalse(catalog.isClosed)
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
  fun categoryRoutesCreateBrowseEntriesWithTheirExactQueries() =
    runTest(mainDispatcher.testDispatcher) {
      categoryRoutesAndQueries.forEach { (route, expectedQuery) ->
        val repository = FakeBeersRepository()
        val pagerFactory = FakeBeersPagerFactory(repository)
        val navigation =
          SharedAppNavigationState(
            repository = repository,
            pagerFactory = pagerFactory,
            coroutineDispatcher = mainDispatcher.dispatcherProvider,
            initialRoute = route,
          )

        try {
          val entry = navigation.current as BrowseBeersEntry

          assertEquals(route.category.name, entry.selection.name)
          assertEquals(expectedQuery, entry.selection.toQuery())
          assertEquals(route, entry.route)
          assertEquals(listOf(expectedQuery), pagerFactory.createdQueries)
        } finally {
          navigation.disposeAll()
        }
      }
    }

  @Test
  fun replacingFromAbsentCategoryRouteClosesDetailsAndKeepsListRoot() =
    runTest(mainDispatcher.testDispatcher) {
      categoryRoutesAndQueries.forEach { (route, expectedQuery) ->
        val repository = FakeBeersRepository()
        val pagerFactory = FakeBeersPagerFactory(repository)
        val navigation =
          SharedAppNavigationState(
            repository = repository,
            pagerFactory = pagerFactory,
            coroutineDispatcher = mainDispatcher.dispatcherProvider,
            initialRoute = PortableRoute.BeersList,
          )

        try {
          val root = navigation.current
          navigation.navigate(PortableRoute.BeerDetail(beer))
          val detail = navigation.current

          navigation.replaceFromRoute(route)
          val category = navigation.current as BrowseBeersEntry

          assertSame(root, navigation.entries.first())
          assertEquals(listOf(root, category), navigation.entries)
          assertTrue(detail.isClosed)
          assertEquals(route, category.route)
          assertEquals(expectedQuery, category.selection.toQuery())
          assertEquals(listOf(expectedQuery), pagerFactory.createdQueries)
          assertTrue(navigation.pop())
          assertTrue(category.isClosed)
          assertSame(root, navigation.current)
        } finally {
          navigation.disposeAll()
        }
      }
    }

  @Test
  fun selectingSavedFilterCreatesPagerForItsExactQuery() {
    val repository = FakeBeersRepository()
    val pagerFactory = FakeBeersPagerFactory(repository)
    val navigation =
      SharedAppNavigationState(
        repository = repository,
        pagerFactory = pagerFactory,
        coroutineDispatcher = DefaultCoroutineDispatcherProvider(),
        initialRoute = PortableRoute.BeersList,
      )
    val preset =
      SavedFilterPreset(
        id = "preset-1",
        name = "IPA by brewery",
        query = BeersQuery(search = "ipa", breweryId = "brewery-1"),
        updatedAt = 1L,
      )

    navigation.navigate(PortableRoute.SavedFilterPresets)
    val savedFilters = navigation.current as SavedFiltersEntry
    navigation.selectSavedFilter(preset)
    val results = navigation.current as SavedFilterResultsEntry

    assertSame(savedFilters, navigation.entries.first())
    assertEquals(preset, results.preset)
    assertEquals(listOf(preset.query), pagerFactory.createdQueries)
    assertTrue(navigation.pop())
    assertTrue(results.isClosed)
    assertSame(savedFilters, navigation.current)
  }

  @Test
  fun savedFilterResultsUseTheirOwnBrowseToolbar() {
    val navigation = navigation()
    navigation.navigate(PortableRoute.SavedFilterPresets)
    val savedFilters = navigation.current
    val preset =
      SavedFilterPreset(
        id = "preset-1",
        name = "IPA",
        query = BeersQuery(search = "ipa"),
        updatedAt = 1L,
      )

    navigation.selectSavedFilter(preset)

    assertTrue(shouldShowShellTopBar(savedFilters))
    assertFalse(shouldShowShellTopBar(navigation.current))
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
