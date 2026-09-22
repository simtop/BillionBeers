package com.simtop.billionbeers.web

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.navigation.contract.PortableRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
