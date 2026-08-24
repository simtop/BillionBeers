package com.simtop.billionbeers.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Semantic spacing tokens for reusable design-system components.
 *
 * Use these roles instead of adding one-off dimensions to a governed component.
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
  val extraHuge: Dp = 64.dp,
)

/**
 * Composition-local override for the current spacing tokens. Prefer [BillionBeersTheme.spacing].
 */
val LocalSpacing = staticCompositionLocalOf { BillionBeersSpacing() }
