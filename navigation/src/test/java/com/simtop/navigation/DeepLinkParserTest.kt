package com.simtop.navigation

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class DeepLinkParserTest {

  @Test
  fun `parses beers list when there is no path segment`() {
    val destination = DeepLinkParser.parse("billionbeers", "beers", emptyList())

    expectThat(destination).isEqualTo(DeepLinkDestination.BeersList)
  }

  @Test
  fun `parses beer detail when the first path segment is a beer id`() {
    val destination = DeepLinkParser.parse("billionbeers", "beers", listOf("42"))

    expectThat(destination).isEqualTo(DeepLinkDestination.BeerDetail("42"))
  }

  @Test
  fun `returns null for a mismatched scheme`() {
    val destination = DeepLinkParser.parse("https", "beers", listOf("42"))

    expectThat(destination).isNull()
  }

  @Test
  fun `returns null for a mismatched host`() {
    val destination = DeepLinkParser.parse("billionbeers", "brewery", listOf("42"))

    expectThat(destination).isNull()
  }

  @Test
  fun `treats a blank path segment as the beers list`() {
    val destination = DeepLinkParser.parse("billionbeers", "beers", listOf(""))

    expectThat(destination).isEqualTo(DeepLinkDestination.BeersList)
  }
}
