package com.simtop.navigation.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteContractTest {

  @Test
  fun `all route values round trip through json`() {
    val routes =
      listOf(
        PortableRoute.BeersList,
        PortableRoute.Favorites,
        PortableRoute.BeersSearch,
        PortableRoute.SavedFilterPresets,
        PortableRoute.BeerBrowse,
        PortableRoute.BeerBrowseSelection(BrowseCategory.Style("ipa", "IPA")),
        PortableRoute.BeerBrowseSelection(BrowseCategory.Brewery("brewery-1", "Brewery")),
        PortableRoute.BeerDetail("42"),
      )

    routes.forEach { route -> assertEquals(route, decodeRoute(encodeRoute(route))) }
  }

  @Test
  fun `route serializer identities remain compatible with saved Android back stacks`() {
    assertEquals("com.simtop.navigation.BeersList", PortableRoute.BeersList.serializer().descriptor.serialName)
    assertEquals("com.simtop.navigation.Favorites", PortableRoute.Favorites.serializer().descriptor.serialName)
    assertEquals("com.simtop.navigation.BeersSearch", PortableRoute.BeersSearch.serializer().descriptor.serialName)
    assertEquals(
      "com.simtop.navigation.SavedFilterPresets",
      PortableRoute.SavedFilterPresets.serializer().descriptor.serialName,
    )
    assertEquals("com.simtop.navigation.BeerBrowse", PortableRoute.BeerBrowse.serializer().descriptor.serialName)
    assertEquals("com.simtop.navigation.BeerDetail", PortableRoute.BeerDetail.serializer().descriptor.serialName)
  }

  @Test
  fun `older detail route payload migrates to its beer id`() {
    val oldPayload =
      """
      {"type":"beer_detail","beer":{"id":"42","name":"Punk IPA","tagline":"Classic","description":"Hoppy","imageUrl":"","abv":5.6,"ibu":41.0,"foodPairing":[]}}
      """.trimIndent()

    val restored = decodeRoute(oldPayload)

    assertEquals(
      PortableRoute.BeerDetail("42"),
      restored,
    )
  }

  @Test
  fun `legacy detail payload ignores fields beyond its beer id`() {
    val payload =
      """
      {"type":"beer_detail","futureRouteField":true,"beer":{"id":"42","name":"Punk IPA","tagline":"Classic","description":"Hoppy","imageUrl":"","abv":5.6,"ibu":41.0,"foodPairing":[],"futureBeerField":"ignored"}}
      """.trimIndent()

    assertEquals(
      PortableRoute.BeerDetail("42"),
      decodeRoute(payload),
    )
  }

  @Test
  fun `serialized detail route contains only its stable id`() {
    val encoded = encodeRoute(PortableRoute.BeerDetail("42"))

    assertTrue(encoded.contains("\"beerId\":\"42\""))
    assertFalse(encoded.contains("Punk IPA"))
  }

  @Test
  fun `deep link parser returns canonical portable routes`() {
    assertEquals(
      PortableRoute.BeersList,
      DeepLinkParser.parse(DeepLinkParser.SCHEME, DeepLinkParser.HOST_BEERS, emptyList()),
    )
    assertEquals(
      PortableRoute.Favorites,
      DeepLinkParser.parse(DeepLinkParser.SCHEME, DeepLinkParser.HOST_FAVORITES, emptyList()),
    )
    assertEquals(
      PortableRoute.Favorites,
      DeepLinkParser.parse(DeepLinkParser.SCHEME, DeepLinkParser.HOST_FAVORITES, listOf("")),
    )
    assertEquals(
      PortableRoute.BeerDetail("42"),
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
      PortableRoute.BeersList,
      DeepLinkParser.parse(DeepLinkParser.SCHEME, DeepLinkParser.HOST_BEERS, listOf("")),
    )
  }
}
