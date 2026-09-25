package com.simtop.billionbeers.shared.beerdetail

import kotlin.test.Test
import kotlin.test.assertEquals

class ServingTemperatureFormatterTest {

  @Test
  fun `formats a temperature range with unit`() {
    assertEquals("4–8°C", formatServingTemperatureRange(4, 8))
  }

  @Test
  fun `formats equal temperatures as a single value`() {
    assertEquals("4°C", formatServingTemperatureRange(4, 4))
  }

  @Test
  fun `formats negative temperatures`() {
    assertEquals("-4–2°C", formatServingTemperatureRange(-4, 2))
  }
}
