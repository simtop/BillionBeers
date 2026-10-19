package com.simtop.billionbeers.shared.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import billionbeers.shared.designsystem.generated.resources.Res
import billionbeers.shared.designsystem.generated.resources.inter_variable
import billionbeers.shared.designsystem.generated.resources.plusjakarta_variable
import org.jetbrains.compose.resources.Font

private val DefaultTypography = Typography()

/** Typography tokens for reusable design-system components. */
val BillionBeersTypography =
  DefaultTypography.copy(
    displayLarge = DefaultTypography.displayLarge,
    displayMedium = DefaultTypography.displayMedium,
    displaySmall = DefaultTypography.displaySmall,
    headlineLarge =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
      ),
    headlineSmall = DefaultTypography.headlineSmall,
    titleLarge =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
      ),
    titleMedium = DefaultTypography.titleMedium,
    titleSmall = DefaultTypography.titleSmall,
    bodyLarge =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
      ),
  )

@Composable
internal fun loadedBillionBeersTypography(): Typography {
  val inter =
    FontFamily(
      Font(Res.font.inter_variable, FontWeight.Normal),
      Font(Res.font.inter_variable, FontWeight.Medium),
      Font(Res.font.inter_variable, FontWeight.SemiBold),
      Font(Res.font.inter_variable, FontWeight.Bold),
    )
  val plusJakartaSans =
    FontFamily(
      Font(Res.font.plusjakarta_variable, FontWeight.Normal),
      Font(Res.font.plusjakarta_variable, FontWeight.Medium),
      Font(Res.font.plusjakarta_variable, FontWeight.SemiBold),
      Font(Res.font.plusjakarta_variable, FontWeight.Bold),
    )
  return remember(inter, plusJakartaSans) {
    BillionBeersTypography.copy(
      displayLarge = BillionBeersTypography.displayLarge.copy(fontFamily = plusJakartaSans),
      displayMedium = BillionBeersTypography.displayMedium.copy(fontFamily = plusJakartaSans),
      displaySmall = BillionBeersTypography.displaySmall.copy(fontFamily = plusJakartaSans),
      headlineLarge = BillionBeersTypography.headlineLarge.copy(fontFamily = plusJakartaSans),
      headlineMedium = BillionBeersTypography.headlineMedium.copy(fontFamily = plusJakartaSans),
      headlineSmall = BillionBeersTypography.headlineSmall.copy(fontFamily = plusJakartaSans),
      titleLarge = BillionBeersTypography.titleLarge.copy(fontFamily = plusJakartaSans),
      titleMedium = BillionBeersTypography.titleMedium.copy(fontFamily = plusJakartaSans),
      titleSmall = BillionBeersTypography.titleSmall.copy(fontFamily = plusJakartaSans),
      bodyLarge = BillionBeersTypography.bodyLarge.copy(fontFamily = inter),
      bodyMedium = BillionBeersTypography.bodyMedium.copy(fontFamily = inter),
      bodySmall = BillionBeersTypography.bodySmall.copy(fontFamily = inter),
      labelLarge = BillionBeersTypography.labelLarge.copy(fontFamily = inter),
      labelMedium = BillionBeersTypography.labelMedium.copy(fontFamily = inter),
      labelSmall = BillionBeersTypography.labelSmall.copy(fontFamily = inter),
    )
  }
}

/** Composition-local override for the current typography tokens. */
val LocalTypography = staticCompositionLocalOf { BillionBeersTypography }
