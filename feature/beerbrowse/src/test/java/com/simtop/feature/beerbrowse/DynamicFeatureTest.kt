package com.simtop.feature.beerbrowse

import com.simtop.feature.beerbrowse.presentation.BeerBrowseProviderImpl
import com.simtop.navigation.DynamicFeature
import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicFeatureTest {

  @Test
  fun `verify beer browse provider class name matches constant`() {
    assertEquals(
      BeerBrowseProviderImpl::class.java.name,
      DynamicFeature.BeerBrowse.providerClass,
    )
  }
}
