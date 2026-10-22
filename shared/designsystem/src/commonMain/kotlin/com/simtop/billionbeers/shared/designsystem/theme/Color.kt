package com.simtop.billionbeers.shared.designsystem.theme

import androidx.compose.material3.ColorScheme
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

private const val AVAILABLE_LIGHT_CONTAINER = 0xFFE8F5E9
private const val AVAILABLE_LIGHT_FOREGROUND = 0xFF1B5E20
private const val AVAILABLE_DARK_CONTAINER = 0xFF123A22
private const val AVAILABLE_DARK_FOREGROUND = 0xFFA5D6B0
private const val NEUTRAL_100 = 0xFFFFFFFF
private const val NEUTRAL_90 = 0xFFE3E2E6
private const val NEUTRAL_10 = 0xFF1B1B1F
private const val WARM_NEUTRAL_30 = 0xFF514537
private const val WARM_NEUTRAL_90_VARIANT = 0xFFEDE1CF
private const val WARM_NEUTRAL_80 = 0xFFD3C4AF
private const val WARM_NEUTRAL_20_INVERSE = 0xFF382F24
private const val WARM_NEUTRAL_95 = 0xFFFAF0E2
private const val ERROR_90 = 0xFFFFDAD6
private const val ERROR_10 = 0xFF410002
private const val WARM_NEUTRAL_60 = 0xFF9E907F
private const val WARM_NEUTRAL_50 = 0xFF827565
private const val WARM_NEUTRAL_25 = 0xFF40382F
private const val WARM_NEUTRAL_85 = 0xFFE8DED0
private const val WARM_NEUTRAL_15 = 0xFF29231D
private const val WARM_NEUTRAL_94 = 0xFFF5EBDD
private const val WARM_NEUTRAL_22 = 0xFF342D25
private const val WARM_NEUTRAL_92 = 0xFFEFE4D4
private const val WARM_NEUTRAL_87 = 0xFFE8DDCC
private const val WARM_NEUTRAL_12 = 0xFF241F19
private const val WARM_NEUTRAL_97 = 0xFFFBF5EA
private const val WARM_NEUTRAL_6 = 0xFF191510

private val Neutral100 = Color(NEUTRAL_100)

private val Error20 = Color(0xFF690005)
private const val ERROR_30 = 0xFF93000A
private val Error30 = Color(ERROR_30)
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
  val abvContainer: Color,
  val onAbvContainer: Color,
  val ibuContainer: Color,
  val onIbuContainer: Color,
  val availableContainer: Color,
  val onAvailableContainer: Color,
  val material: ColorScheme,
) {
  val primary: Color
    get() = material.primary

  val onPrimary: Color
    get() = material.onPrimary

  val primaryContainer: Color
    get() = material.primaryContainer

  val onPrimaryContainer: Color
    get() = material.onPrimaryContainer

  val secondary: Color
    get() = material.secondary

  val onSecondary: Color
    get() = material.onSecondary

  val background: Color
    get() = material.background

  val onBackground: Color
    get() = material.onBackground

  val surface: Color
    get() = material.surface

  val onSurface: Color
    get() = material.onSurface

  val error: Color
    get() = material.error

  val onError: Color
    get() = material.onError
}

val BillionBeersLightColors =
  BillionBeersColors(
    abvContainer = Teal90,
    onAbvContainer = Teal30,
    ibuContainer = DeepOrange90,
    onIbuContainer = DeepOrange40,
    availableContainer = Color(AVAILABLE_LIGHT_CONTAINER),
    onAvailableContainer = Color(AVAILABLE_LIGHT_FOREGROUND),
    material = brandMaterialColors(dark = false),
  )

val BillionBeersDarkColors =
  BillionBeersColors(
    abvContainer = Teal20,
    onAbvContainer = Teal80,
    ibuContainer = DeepOrange20,
    onIbuContainer = DeepOrange80,
    availableContainer = Color(AVAILABLE_DARK_CONTAINER),
    onAvailableContainer = Color(AVAILABLE_DARK_FOREGROUND),
    material = brandMaterialColors(dark = true),
  )

/** Composition-local override for the current semantic color roles. */
val LocalColors = staticCompositionLocalOf { BillionBeersLightColors }

/** Complete warm palette, also available to Android OS surfaces outside Compose. */
private fun brandMaterialColors(dark: Boolean): ColorScheme {
  fun choose(night: Long, day: Long) = Color(if (dark) night else day)
  val primary = choose(AMBER_80, AMBER_40)
  val onPrimary = choose(AMBER_20, NEUTRAL_100)
  val container = choose(AMBER_30, AMBER_90)
  val onContainer = choose(AMBER_90, AMBER_10)
  return ColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = container,
    onPrimaryContainer = onContainer,
    secondary = primary,
    onSecondary = onPrimary,
    background = choose(WARM_NEUTRAL_10, PAPER_95),
    onBackground = choose(NEUTRAL_90, NEUTRAL_10),
    surface = choose(WARM_NEUTRAL_20, PAPER_100),
    onSurface = choose(WARM_NEUTRAL_90, INK_10),
    error = if (dark) Error80 else Error30,
    onError = if (dark) Error20 else Neutral100,
    inversePrimary = choose(AMBER_40, AMBER_80),
    secondaryContainer = container,
    onSecondaryContainer = onContainer,
    tertiary = primary,
    onTertiary = onPrimary,
    tertiaryContainer = container,
    onTertiaryContainer = onContainer,
    surfaceVariant = choose(WARM_NEUTRAL_30, WARM_NEUTRAL_90_VARIANT),
    onSurfaceVariant = choose(WARM_NEUTRAL_80, WARM_NEUTRAL_30),
    surfaceTint = primary,
    inverseSurface = choose(WARM_NEUTRAL_90, WARM_NEUTRAL_20_INVERSE),
    inverseOnSurface = choose(WARM_NEUTRAL_20_INVERSE, WARM_NEUTRAL_95),
    errorContainer = choose(ERROR_30, ERROR_90),
    onErrorContainer = choose(ERROR_90, ERROR_10),
    outline = choose(WARM_NEUTRAL_60, WARM_NEUTRAL_50),
    outlineVariant = choose(WARM_NEUTRAL_30, WARM_NEUTRAL_80),
    scrim = Color.Black,
    surfaceBright = choose(WARM_NEUTRAL_25, PAPER_100),
    surfaceDim = choose(WARM_NEUTRAL_10, WARM_NEUTRAL_85),
    surfaceContainer = choose(WARM_NEUTRAL_15, WARM_NEUTRAL_94),
    surfaceContainerHigh = choose(WARM_NEUTRAL_22, WARM_NEUTRAL_92),
    surfaceContainerHighest = choose(WARM_NEUTRAL_25, WARM_NEUTRAL_87),
    surfaceContainerLow = choose(WARM_NEUTRAL_12, WARM_NEUTRAL_97),
    surfaceContainerLowest = choose(WARM_NEUTRAL_6, NEUTRAL_100),
    primaryFixed = Color(AMBER_90),
    primaryFixedDim = Color(AMBER_80),
    onPrimaryFixed = Color(AMBER_10),
    onPrimaryFixedVariant = Color(AMBER_30),
    secondaryFixed = Color(AMBER_90),
    secondaryFixedDim = Color(AMBER_80),
    onSecondaryFixed = Color(AMBER_10),
    onSecondaryFixedVariant = Color(AMBER_30),
    tertiaryFixed = Color(AMBER_90),
    tertiaryFixedDim = Color(AMBER_80),
    onTertiaryFixed = Color(AMBER_10),
    onTertiaryFixedVariant = Color(AMBER_30),
  )
}
