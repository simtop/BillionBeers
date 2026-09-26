package com.simtop.billionbeers.shared.beerdetail

import kotlin.test.Test
import kotlin.test.assertEquals

class BeerMetricFormatterTest {

  @Test
  fun `formats whole values without a trailing decimal`() {
    assertEquals("60", formatBeerMetric(60.0))
  }

  @Test
  fun `preserves fractional values`() {
    assertEquals("41.5", formatBeerMetric(41.5))
  }

  @Test
  fun `formats zero without a trailing decimal`() {
    assertEquals("0", formatBeerMetric(0.0))
  }
}
