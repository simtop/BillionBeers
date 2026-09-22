package com.simtop.billionbeers.iosshared

import com.simtop.navigation.contract.DeepLinkDestination
import com.simtop.navigation.contract.DeepLinkParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosHostAdapterTest {
  @Test
  fun deepLinksUseThePortableContract() {
    assertEquals(
      DeepLinkDestination.BeersList,
      parseIosDeepLink(DeepLinkParser.SCHEME, DeepLinkParser.HOST_BEERS, emptyList()),
    )
    assertEquals(
      DeepLinkDestination.Favorites,
      parseIosDeepLink(DeepLinkParser.SCHEME, DeepLinkParser.HOST_FAVORITES, emptyList()),
    )
    assertEquals(
      DeepLinkDestination.BeerDetail("42"),
      parseIosDeepLink(DeepLinkParser.SCHEME, DeepLinkParser.HOST_BEERS, listOf("42")),
    )
  }

  @Test
  fun malformedDeepLinksAreIgnored() {
    assertNull(parseIosDeepLink("https", DeepLinkParser.HOST_BEERS, emptyList()))
    assertNull(parseIosDeepLink(DeepLinkParser.SCHEME, "unknown", emptyList()))
    assertNull(parseIosDeepLink(DeepLinkParser.SCHEME, DeepLinkParser.HOST_FAVORITES, listOf("extra")))
    assertEquals(
      DeepLinkDestination.BeersList,
      parseIosDeepLink(DeepLinkParser.SCHEME, DeepLinkParser.HOST_BEERS, listOf("")),
    )
  }

  @Test
  fun languageCodesNormalizeToSupportedCatalogs() {
    assertEquals("en", iosLanguage("en-US"))
    assertEquals("fr", iosLanguage("fr-CA"))
    assertEquals("en", iosLanguage("ar"))
  }
}
