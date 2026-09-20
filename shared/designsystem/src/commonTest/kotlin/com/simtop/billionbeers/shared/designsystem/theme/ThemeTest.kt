package com.simtop.billionbeers.shared.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeTest {
  @Test
  fun spacingDefaultsRemainSemanticAndImmutable() {
    val spacing = BillionBeersSpacing()

    assertEquals(4f, spacing.extraSmall.value)
    assertEquals(16f, spacing.medium.value)
    assertEquals(64f, spacing.extraHuge.value)
  }

  @Test
  fun colorTokensExposeDistinctLightAndDarkRoles() {
    assertEquals(Color(0xFF0047AB), BillionBeersLightColors.primary)
    assertEquals(Color(0xFFADC6FF), BillionBeersDarkColors.primary)
    assertEquals(Color(0xFFF1F0F4), BillionBeersLightColors.background)
    assertEquals(Color(0xFF1B1B1F), BillionBeersDarkColors.background)
  }

  @Test
  fun typographyKeepsGovernedTitleStyle() {
    assertEquals(22f, BillionBeersTypography.titleLarge.fontSize.value)
    assertEquals(28f, BillionBeersTypography.titleLarge.lineHeight.value)
  }
}
