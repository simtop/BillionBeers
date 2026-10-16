package com.simtop.navigation

import com.simtop.navigation.contract.PortableRoute
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class DynamicFeatureRouteTest {
  @Test
  fun `portable dynamic routes map to Android split metadata`() {
    expectThat(PortableRoute.BeerBrowse.dynamicFeatureOrNull()).isEqualTo(DynamicFeature.BeerBrowse)
    expectThat(PortableRoute.BeerDetail("beer-1").dynamicFeatureOrNull())
      .isEqualTo(DynamicFeature.BeerDetail)
  }

  @Test
  fun `portable non dynamic routes do not request a split`() {
    expectThat(PortableRoute.BeersList.dynamicFeatureOrNull()).isNull()
  }
}
