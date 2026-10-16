package com.simtop.core.core

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AndroidLanguageProviderTest {

  @Test
  fun `returns the default locale language`() {
    assertEquals(Locale.getDefault().language, AndroidLanguageProvider().currentLanguageCode())
  }
}
