package com.simtop.billionbeers.shared.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Applies the portable BillionBeers semantic tokens and maps them to Material 3.
 *
 * Hosts choose the dark-mode value explicitly. Android-only defaults such as system dark mode and
 * dynamic color remain in the Android facade.
 */
@Composable
fun BillionBeersTheme(
  darkTheme: Boolean,
  colors: BillionBeersColors? = null,
  spacing: BillionBeersSpacing = BillionBeersSpacing(),
  typography: androidx.compose.material3.Typography? = null,
  content: @Composable () -> Unit,
) {
  val resolvedColors = colors ?: if (darkTheme) BillionBeersDarkColors else BillionBeersLightColors
  val resolvedTypography = typography ?: loadedBillionBeersTypography()
  CompositionLocalProvider(
    LocalSpacing provides spacing,
    LocalColors provides resolvedColors,
    LocalTypography provides resolvedTypography,
  ) {
    MaterialTheme(
      colorScheme = resolvedColors.material,
      typography = resolvedTypography,
      content = content,
    )
  }
}

/** Supported composable accessors for the shared design-system tokens. */
object BillionBeersTheme {
  val brand: BillionBeersBrand = BillionBeersBrand

  val spacing: BillionBeersSpacing
    @Composable @ReadOnlyComposable get() = LocalSpacing.current

  val colors: BillionBeersColors
    @Composable @ReadOnlyComposable get() = LocalColors.current

  val colorScheme: ColorScheme
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme

  val typography: androidx.compose.material3.Typography
    @Composable @ReadOnlyComposable get() = LocalTypography.current
}
