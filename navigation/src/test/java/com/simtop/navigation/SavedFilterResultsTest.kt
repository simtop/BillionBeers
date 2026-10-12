package com.simtop.navigation

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class SavedFilterResultsTest {
  @Test
  fun `saved filter result preserves its exact query`() {
    val query = SavedFilterResults("preset-1", "IPA", "ipa", "style-1", "brewery-1").toQuery()

    expectThat(query.search).isEqualTo("ipa")
    expectThat(query.styleId).isEqualTo("style-1")
    expectThat(query.breweryId).isEqualTo("brewery-1")
  }
}
