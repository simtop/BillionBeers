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

private val Teal20 = Color(0xFF00363A)
private val Teal30 = Color(0xFF006064)
private val Teal80 = Color(0xFF80DEEA)
private val Teal90 = Color(0xFFE0F7FA)
private val DeepOrange20 = Color(0xFF5D1C00)
private val DeepOrange40 = Color(0xFFBF360C)
private val DeepOrange80 = Color(0xFFFFAB91)
private val DeepOrange90 = Color(0xFFFBE9E7)

/**
 * Semantic color roles exposed by [BillionBeersTheme].
 *
 * Metric containers and their on-colors are supported display roles for ABV/IBU chips and cards.
 * Use the opaque on-color for both values and small labels to preserve text contrast.
 */
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
  val abvContainer: Color,
  val onAbvContainer: Color,
  val ibuContainer: Color,
  val onIbuContainer: Color,
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
    abvContainer = Teal90,
    onAbvContainer = Teal30,
    ibuContainer = DeepOrange90,
    onIbuContainer = DeepOrange40,
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
    abvContainer = Teal20,
    onAbvContainer = Teal80,
    ibuContainer = DeepOrange20,
    onIbuContainer = DeepOrange80,
  )

/** Composition-local override for the current semantic color roles. */
val LocalColors = staticCompositionLocalOf { BillionBeersLightColors }
