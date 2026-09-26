package com.simtop.billionbeers.shared.app

import kotlin.test.Test
import kotlin.test.assertEquals

class QuantityFormatterTest {

  @Test
  fun `uses plural form for zero`() {
    assertEquals("0 beers", formatCount(0, "beer", "beers"))
  }

  @Test
  fun `uses singular form for one`() {
    assertEquals("1 beer", formatCount(1, "beer", "beers"))
  }

  @Test
  fun `uses plural form for values above one`() {
    assertEquals("2 beers", formatCount(2, "beer", "beers"))
  }
}
