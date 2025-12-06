package com.simtop.feature.beerdetail

import com.simtop.feature.beerdetail.presentation.BeerDetailProviderImpl
import com.simtop.navigation.FeatureConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureConstantsTest {

  @Test
  fun `verify beer detail provider class name matches constant`() {
    assertEquals(
      BeerDetailProviderImpl::class.java.name,
      FeatureConstants.BEER_DETAIL_PROVIDER_CLASS
    )
  }
}
