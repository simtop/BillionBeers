package com.simtop.billionbeers.shared.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private const val AMBER_40 = 0xFF875016
private const val AMBER_80 = 0xFFF1BD79
private const val AMBER_90 = 0xFFF4DFBB
private const val AMBER_10 = 0xFF321A05
private const val AMBER_20 = 0xFF3E260B
private const val AMBER_30 = 0xFF5C3A18
private const val PAPER_95 = 0xFFF5F0E7
private const val PAPER_100 = 0xFFFFFCF6
private const val INK_10 = 0xFF2D251B
private const val WARM_NEUTRAL_10 = 0xFF201C17
private const val WARM_NEUTRAL_20 = 0xFF302A23
private const val WARM_NEUTRAL_90 = 0xFFF3EADF

private val Neutral10 = Color(0xFF1B1B1F)
private val Neutral90 = Color(0xFFE3E2E6)
private val Neutral100 = Color(0xFFFFFFFF)

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
    primary = Color(AMBER_40),
    onPrimary = Neutral100,
    primaryContainer = Color(AMBER_90),
    onPrimaryContainer = Color(AMBER_10),
    secondary = Color(AMBER_40),
    onSecondary = Neutral100,
    background = Color(PAPER_95),
    onBackground = Neutral10,
    surface = Color(PAPER_100),
    onSurface = Color(INK_10),
    error = Error30,
    onError = Neutral100,
    abvContainer = Teal90,
    onAbvContainer = Teal30,
    ibuContainer = DeepOrange90,
    onIbuContainer = DeepOrange40,
  )

internal val BillionBeersDarkColors =
  BillionBeersColors(
    primary = Color(AMBER_80),
    onPrimary = Color(AMBER_20),
    primaryContainer = Color(AMBER_30),
    onPrimaryContainer = Color(AMBER_90),
    secondary = Color(AMBER_80),
    onSecondary = Color(AMBER_20),
    background = Color(WARM_NEUTRAL_10),
    onBackground = Neutral90,
    surface = Color(WARM_NEUTRAL_20),
    onSurface = Color(WARM_NEUTRAL_90),
    error = Error80,
    onError = Error20,
    abvContainer = Teal20,
    onAbvContainer = Teal80,
    ibuContainer = DeepOrange20,
    onIbuContainer = DeepOrange80,
  )

/** Composition-local override for the current semantic color roles. */
val LocalColors = staticCompositionLocalOf { BillionBeersLightColors }
