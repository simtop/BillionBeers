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
 * BillionBeers Design System Theme wrapper.
 * This integrates our custom tiered tokens with the standard Material3 system.
 */
@Composable
fun BillionBeersTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) BillionBeersDarkColors else BillionBeersLightColors
    
    // Map our custom roles to Material3 roles for full compatibility
    val colorScheme = colors.toMaterialColorScheme()

    CompositionLocalProvider(
        LocalSpacing provides BillionBeersSpacing(),
        LocalColors provides colors,
        LocalTypography provides BillionBeersTypography
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BillionBeersTypography,
            content = content
        )
    }
}

/**
 * Convenience object to access tokens within Composable functions.
 */
object BillionBeersTheme {
    val spacing: BillionBeersSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current

    val colors: BillionBeersColors
        @Composable
        @ReadOnlyComposable
        get() = LocalColors.current

    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = LocalTypography.current
}


/**
 * Helper to map custom tokens to Material3 ColorScheme.
 * This ensures that standard Material components (like Buttons, Cards)
 * automatically use our design system colors.
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
            onError = onError
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
            onError = onError
        )
    }
}
