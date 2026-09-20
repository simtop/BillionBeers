package com.simtop.billionbeers.shared.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
  typography: androidx.compose.material3.Typography = BillionBeersTypography,
  content: @Composable () -> Unit,
) {
  val resolvedColors = colors ?: if (darkTheme) BillionBeersDarkColors else BillionBeersLightColors
  CompositionLocalProvider(
    LocalSpacing provides spacing,
    LocalColors provides resolvedColors,
    LocalTypography provides typography,
  ) {
    MaterialTheme(
      colorScheme = resolvedColors.toMaterialColorScheme(darkTheme),
      typography = typography,
      content = content,
    )
  }
}

/** Supported composable accessors for the shared design-system tokens. */
object BillionBeersTheme {
  val spacing: BillionBeersSpacing
    @Composable @ReadOnlyComposable get() = LocalSpacing.current

  val colors: BillionBeersColors
    @Composable @ReadOnlyComposable get() = LocalColors.current

  val typography: androidx.compose.material3.Typography
    @Composable @ReadOnlyComposable get() = LocalTypography.current
}

private fun BillionBeersColors.toMaterialColorScheme(darkTheme: Boolean): ColorScheme {
  return if (!darkTheme) {
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
