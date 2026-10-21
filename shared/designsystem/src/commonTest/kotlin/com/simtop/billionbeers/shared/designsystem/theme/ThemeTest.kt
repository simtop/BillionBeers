package com.simtop.billionbeers.shared.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    assertEquals(Color(0xFF875016), BillionBeersLightColors.primary)
    assertEquals(Color(0xFFF1BD79), BillionBeersDarkColors.primary)
    assertEquals(Color(0xFFF5F0E7), BillionBeersLightColors.background)
    assertEquals(Color(0xFF201C17), BillionBeersDarkColors.background)
  }

  @Test
  fun typographyKeepsGovernedTitleStyle() {
    assertEquals(22f, BillionBeersTypography.titleLarge.fontSize.value)
    assertEquals(28f, BillionBeersTypography.titleLarge.lineHeight.value)
  }

  @Test
  fun foregroundsMeetNormalTextContrastInBothThemes() {
    for (colors in listOf(BillionBeersLightColors, BillionBeersDarkColors)) {
      val pairs =
        listOf(
          "Primary" to (colors.onPrimary to colors.primary),
          "Primary container" to (colors.onPrimaryContainer to colors.primaryContainer),
          "Secondary" to (colors.onSecondary to colors.secondary),
          "Surface" to (colors.onSurface to colors.surface),
          "Background" to (colors.onBackground to colors.background),
          "ABV" to (colors.onAbvContainer to colors.abvContainer),
          "IBU" to (colors.onIbuContainer to colors.ibuContainer),
        )
      for ((name, pair) in pairs) {
        val (foreground, background) = pair
        assertEquals(1f, foreground.alpha, "$name foreground must remain opaque")
        assertEquals(1f, background.alpha, "$name container must remain opaque")
        val ratio = contrastRatio(foreground, background)
        assertTrue(ratio >= 4.5, "$name normal-text contrast was $ratio:1; expected at least 4.5:1")
      }
    }
  }

  private fun contrastRatio(first: Color, second: Color): Double {
    val firstLuminance = relativeLuminance(first)
    val secondLuminance = relativeLuminance(second)
    return (maxOf(firstLuminance, secondLuminance) + 0.05) /
      (minOf(firstLuminance, secondLuminance) + 0.05)
  }

  private fun relativeLuminance(color: Color): Double {
    fun linearize(channel: Float): Double =
      if (channel <= 0.04045f) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

    return 0.2126 * linearize(color.red) +
      0.7152 * linearize(color.green) +
      0.0722 * linearize(color.blue)
  }
}
