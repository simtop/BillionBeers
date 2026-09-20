package com.simtop.navigation.contract

import com.simtop.beerdomain.domain.models.Beer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteContractTest {

  private val beer =
    Beer(
      id = "42",
      name = "Punk IPA",
      tagline = "Post Modern Classic.",
      description = "A bright, hoppy beer.",
      imageUrl = "https://example.test/punk.png",
      abv = 5.6,
      ibu = 41.0,
      foodPairing = listOf("Spicy food"),
      availability = true,
      isFavorite = true,
      styleName = "IPA",
      breweryName = "Brewdog",
      srm = 8,
      releasedYear = 2007,
      minServingTemperature = 8,
      maxServingTemperature = 12,
      fermentationMethod = "Top",
      ingredients = listOf("Malt"),
      recommendedGlasses = listOf("Tulip"),
    )

  @Test
  fun `all route values round trip through json`() {
    val routes =
      listOf(
        PortableRoute.BeersList,
        PortableRoute.Favorites,
        PortableRoute.BeersSearch,
        PortableRoute.BeerBrowse,
        PortableRoute.BeerDetail(beer),
      )

    routes.forEach { route -> assertEquals(route, decodeRoute(encodeRoute(route))) }
  }

  @Test
  fun `older detail payload receives current default fields`() {
    val oldPayload =
      """
      {"type":"beer_detail","beer":{"id":"42","name":"Punk IPA","tagline":"Classic","description":"Hoppy","imageUrl":"","abv":5.6,"ibu":41.0,"foodPairing":[]}}
      """.trimIndent()

    val restored = decodeRoute(oldPayload)

    assertEquals(
      PortableRoute.BeerDetail(
        Beer(
          id = "42",
          name = "Punk IPA",
          tagline = "Classic",
          description = "Hoppy",
          imageUrl = "",
          abv = 5.6,
          ibu = 41.0,
          foodPairing = emptyList(),
        )
      ),
      restored,
    )
  }

  @Test
  fun `parser accepts list favorites and detail destinations`() {
    assertEquals(
      DeepLinkDestination.BeersList,
      DeepLinkParser.parse(DeepLinkParser.SCHEME, DeepLinkParser.HOST_BEERS, emptyList()),
    )
    assertEquals(
      DeepLinkDestination.Favorites,
      DeepLinkParser.parse(DeepLinkParser.SCHEME, DeepLinkParser.HOST_FAVORITES, emptyList()),
    )
    assertEquals(
      DeepLinkDestination.BeerDetail("42"),
      DeepLinkParser.parse(DeepLinkParser.SCHEME, DeepLinkParser.HOST_BEERS, listOf("42")),
    )
  }

  @Test
  fun `parser rejects invalid or ambiguous destinations`() {
    assertNull(DeepLinkParser.parse("https", DeepLinkParser.HOST_BEERS, listOf("42")))
    assertNull(DeepLinkParser.parse(DeepLinkParser.SCHEME, "unknown", emptyList()))
    assertNull(
      DeepLinkParser.parse(DeepLinkParser.SCHEME, DeepLinkParser.HOST_FAVORITES, listOf("extra"))
    )
    assertEquals(
      DeepLinkDestination.BeersList,
      DeepLinkParser.parse(DeepLinkParser.SCHEME, DeepLinkParser.HOST_BEERS, listOf("")),
    )
  }
}
