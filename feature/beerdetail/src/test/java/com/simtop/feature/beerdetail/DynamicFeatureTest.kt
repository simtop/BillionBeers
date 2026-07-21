package com.simtop.feature.beerdetail

import com.simtop.feature.beerdetail.presentation.BeerDetailProviderImpl
import com.simtop.navigation.DynamicFeature
import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicFeatureTest {

  @Test
  fun `verify beer detail provider class name matches constant`() {
    assertEquals(
      BeerDetailProviderImpl::class.java.name,
      DynamicFeature.BeerDetail.providerClass,
    )
  }
}
