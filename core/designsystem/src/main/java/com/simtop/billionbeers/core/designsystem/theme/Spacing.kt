package com.simtop.billionbeers.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * BillionBeers Spacing Tokens
 * Using a standard 4dp/8dp grid system.
 */
@Immutable
data class BillionBeersSpacing(
    val default: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val huge: Dp = 48.dp,
    val extraHuge: Dp = 64.dp
)

val LocalSpacing = staticCompositionLocalOf { BillionBeersSpacing() }
