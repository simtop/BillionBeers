package com.simtop.billionbeers.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme as SharedBillionBeersTheme

/**
 * Android facade for the shared design-system theme.
 *
 * Android chooses the default system-dark value; the shared implementation receives that choice
 * explicitly and owns the portable Material 3/token mapping.
 */
@Composable
fun BillionBeersTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  SharedBillionBeersTheme(darkTheme = darkTheme, content = content)
}

/** Stable Android-facing accessors for the shared semantic tokens. */
object BillionBeersTheme {
  val spacing: BillionBeersSpacing
    @Composable @ReadOnlyComposable get() = SharedBillionBeersTheme.spacing

  val colors: BillionBeersColors
    @Composable @ReadOnlyComposable get() = SharedBillionBeersTheme.colors

  val typography: Typography
    @Composable @ReadOnlyComposable get() = SharedBillionBeersTheme.typography
}
