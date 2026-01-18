package com.simtop.billionbeers.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * BillionBeers Primitive Color Tokens
 * These are the raw values. In a Figma-sync future, these would be the values
 * updated by the automation scripts.
 */
internal val Blue10 = Color(0xFF001945)
internal val Blue20 = Color(0xFF002F6E)
internal val Blue30 = Color(0xFF0047AB) // Primary
internal val Blue40 = Color(0xFF1E5BBF)
internal val Blue80 = Color(0xFFADC6FF)
internal val Blue90 = Color(0xFFD8E2FF)

internal val Neutral10 = Color(0xFF1B1B1F)
internal val Neutral20 = Color(0xFF303033)
internal val Neutral90 = Color(0xFFE3E2E6)
internal val Neutral95 = Color(0xFFF1F0F4)
internal val Neutral100 = Color(0xFFFFFFFF)

internal val Error10 = Color(0xFF410002)
internal val Error20 = Color(0xFF690005)
internal val Error30 = Color(0xFF93000A)
internal val Error80 = Color(0xFFFFB4AB)
internal val Error90 = Color(0xFFFFDAD6)

/**
 * BillionBeers Semantic Color Tokens
 * These define the "roles" of colors in the UI.
 * They reference the Primitives above.
 */
internal val BillionBeersLightColors = BillionBeersColors(
    primary = Blue30,
    onPrimary = Neutral100,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,
    secondary = Blue40,
    onSecondary = Neutral100,
    background = Neutral95,
    onBackground = Neutral10,
    surface = Neutral100,
    onSurface = Neutral10,
    error = Error30,
    onError = Neutral100
)

internal val BillionBeersDarkColors = BillionBeersColors(
    primary = Blue80,
    onPrimary = Blue20,
    primaryContainer = Blue30,
    onPrimaryContainer = Blue90,
    secondary = Blue80,
    onSecondary = Blue20,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral20,
    onSurface = Neutral90,
    error = Error80,
    onError = Error20
)

data class BillionBeersColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val error: Color,
    val onError: Color
)
