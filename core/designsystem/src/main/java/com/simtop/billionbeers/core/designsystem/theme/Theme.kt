package com.simtop.billionbeers.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Applies the BillionBeers semantic tokens and maps them to the standard Material3 theme.
 *
 * This is the supported entry point for application and feature UI. Reusable components should read
 * colors, spacing, and typography through [BillionBeersTheme] inside this scope.
 */
@Composable
fun BillionBeersTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  val colors = if (darkTheme) BillionBeersDarkColors else BillionBeersLightColors

  // Map our custom roles to Material3 roles for full compatibility
  val colorScheme = colors.toMaterialColorScheme()

  CompositionLocalProvider(
    LocalSpacing provides BillionBeersSpacing(),
    LocalColors provides colors,
    LocalTypography provides BillionBeersTypography,
  ) {
    MaterialTheme(colorScheme = colorScheme, typography = BillionBeersTypography, content = content)
  }
}

/**
 * Supported composable accessors for the BillionBeers design-system tokens.
 *
 * These accessors intentionally hide the underlying composition locals so consumers depend on
 * semantic roles rather than primitive palette values.
 */
object BillionBeersTheme {
  /** Current semantic spacing tokens. */
  val spacing: BillionBeersSpacing
    @Composable @ReadOnlyComposable get() = LocalSpacing.current

  /** Current semantic color roles. */
  val colors: BillionBeersColors
    @Composable @ReadOnlyComposable get() = LocalColors.current

  /** Current typography tokens. */
  val typography: Typography
    @Composable @ReadOnlyComposable get() = LocalTypography.current
}

/**
 * Helper to map custom tokens to Material3 ColorScheme. This ensures that standard Material
 * components (like Buttons, Cards) automatically use our design system colors.
 */
private fun BillionBeersColors.toMaterialColorScheme(): ColorScheme {
  return if (this == BillionBeersLightColors) {
    lightColorScheme(
      primary = primary,
      onPrimary = onPrimary,
      primaryContainer = primaryContainer,
      onPrimaryContainer = onPrimaryContainer,
      secondary = secondary,
      onSecondary = onSecondary,
      background = background,
      onBackground = onBackground,
      surface = surface,
      onSurface = onSurface,
      error = error,
      onError = onError,
    )
  } else {
    darkColorScheme(
      primary = primary,
      onPrimary = onPrimary,
      primaryContainer = primaryContainer,
      onPrimaryContainer = onPrimaryContainer,
      secondary = secondary,
      onSecondary = onSecondary,
      background = background,
      onBackground = onBackground,
      surface = surface,
      onSurface = onSurface,
      error = error,
      onError = onError,
    )
  }
}
