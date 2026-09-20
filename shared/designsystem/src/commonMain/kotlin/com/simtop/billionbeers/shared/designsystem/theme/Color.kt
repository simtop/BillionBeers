package com.simtop.billionbeers.shared.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val Blue10 = Color(0xFF001945)
private val Blue20 = Color(0xFF002F6E)
private val Blue30 = Color(0xFF0047AB)
private val Blue40 = Color(0xFF1E5BBF)
private val Blue80 = Color(0xFFADC6FF)
private val Blue90 = Color(0xFFD8E2FF)

private val Neutral10 = Color(0xFF1B1B1F)
private val Neutral20 = Color(0xFF303033)
private val Neutral90 = Color(0xFFE3E2E6)
private val Neutral95 = Color(0xFFF1F0F4)
private val Neutral100 = Color(0xFFFFFFFF)

private val Error10 = Color(0xFF410002)
private val Error20 = Color(0xFF690005)
private val Error30 = Color(0xFF93000A)
private val Error80 = Color(0xFFFFB4AB)

/** Semantic color roles exposed by [BillionBeersTheme]. */
@Immutable
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
  val onError: Color,
)

internal val BillionBeersLightColors =
  BillionBeersColors(
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
    onError = Neutral100,
  )

internal val BillionBeersDarkColors =
  BillionBeersColors(
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
    onError = Error20,
  )

/** Composition-local override for the current semantic color roles. */
val LocalColors = staticCompositionLocalOf { BillionBeersLightColors }
