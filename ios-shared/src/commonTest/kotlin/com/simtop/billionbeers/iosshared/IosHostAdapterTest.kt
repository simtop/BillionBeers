package com.simtop.billionbeers.iosshared

import kotlin.test.Test
import kotlin.test.assertEquals

class IosHostAdapterTest {
  @Test
  fun languageCodesNormalizeToSupportedCatalogs() {
    assertEquals("en", iosLanguage("en-US"))
    assertEquals("fr", iosLanguage("fr-CA"))
    assertEquals("en", iosLanguage("ar"))
  }
}
