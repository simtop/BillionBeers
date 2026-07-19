package com.simtop.feature.beerbrowse

import com.simtop.feature.beerbrowse.presentation.BeerBrowseProviderImpl
import com.simtop.navigation.FeatureConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureConstantsTest {

  @Test
  fun `verify beer browse provider class name matches constant`() {
    assertEquals(
      BeerBrowseProviderImpl::class.java.name,
      FeatureConstants.BEER_BROWSE_PROVIDER_CLASS,
    )
  }
}
