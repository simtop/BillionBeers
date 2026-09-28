package com.simtop.billionbeers.web

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.navigation.contract.PortableRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebRoutesTest {

  @Test
  fun supportedHashesMapToKnownDestinations() {
    assertEquals(WebRouteDestination.Catalog, parseWebHash(""))
    assertEquals(WebRouteDestination.Catalog, parseWebHash("#catalog"))
    assertEquals(WebRouteDestination.Favorites, parseWebHash("#favorites"))
    assertEquals(WebRouteDestination.Search, parseWebHash("#search"))
    assertEquals(WebRouteDestination.Browse, parseWebHash("#browse"))
    assertEquals(WebRouteDestination.BeerDetail("beer-42"), parseWebHash("#beer/beer-42"))
  }

  @Test
  fun unknownOrIncompleteHashesAreRejected() {
    assertNull(parseWebHash("#beer/"))
    assertNull(parseWebHash("#unknown"))
    assertNull(parseWebHash("https://example.test/#favorites"))
  }

  @Test
  fun browserHistoryBackAndForwardPreserveHashOrdering() {
    val history = WebHistoryFixture(PortableRoute.BeersList)
    history.push(PortableRoute.BeerBrowse)
    history.push(
      PortableRoute.BeerBrowseSelection(
        com.simtop.navigation.contract.BrowseCategory.Style("ipa", "IPA")
      )
    )

    assertEquals("#browse/style/ipa", history.currentHash)
    assertTrue(history.back())
    assertEquals("#browse", history.currentHash)
    assertTrue(history.forward())
    assertEquals("#browse/style/ipa", history.currentHash)
    assertFalse(history.forward())
  }

  @Test
  fun reloadRestoresTheCurrentHashAsTheWebRoot() {
    val history = WebHistoryFixture(PortableRoute.BeersList)
    history.push(PortableRoute.Favorites)
    history.push(PortableRoute.SavedFilterPresets)

    assertEquals(WebRouteDestination.SavedFilters, history.reloadRoot())
  }

  @Test
  fun repeatedRoutesDoNotCreateDuplicateBrowserEntries() {
    val history = WebHistoryFixture(PortableRoute.BeersList)

    history.push(PortableRoute.BeerBrowse)
    history.push(PortableRoute.BeerBrowse)

    assertEquals(2, history.historySize)
    assertEquals("#browse", history.currentHash)
    assertTrue(history.back())
    assertEquals("#catalog", history.currentHash)
  }

  @Test
  fun pushingAfterBackDiscardsForwardHistory() {
    val history = WebHistoryFixture(PortableRoute.BeersList)
    history.push(PortableRoute.Favorites)
    history.push(PortableRoute.SavedFilterPresets)
    history.back()

    history.push(PortableRoute.BeerBrowse)

    assertEquals("#browse", history.currentHash)
    assertFalse(history.forward())
  }

  @Test
  fun malformedHashCannotBecomeARestoredWebRoot() {
    val history = WebHistoryFixture("#browse/style/")

    assertNull(parseWebHash("#browse/style/"))
    assertNull(parseWebHash("#beer/beer-42/extra"))
    assertNull(history.reloadRoot())
  }

  @Test
  fun publicHashesContainOnlyRouteKindAndBeerId() {
    val beer =
      Beer(
        id = "beer-42",
        name = "Fixture",
        tagline = "",
        description = "",
        imageUrl = "",
        abv = 0.0,
        ibu = 0.0,
        foodPairing = emptyList(),
      )
    assertEquals("#catalog", PortableRoute.BeersList.toWebHash())
    assertEquals("#favorites", PortableRoute.Favorites.toWebHash())
    assertEquals("#beer/beer-42", PortableRoute.BeerDetail(beer).toWebHash())
  }
}
