package com.simtop.billionbeers.shared.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Distinctive catalog and detail roles, layered on the active Material typography.
 *
 * Edit these roles for a rebrand; the general palette, type scale and spacing remain in Color.kt,
 * Type.kt and Spacing.kt. Explicit host title overrides still take precedence. Scroll policy, touch
 * targets and responsive constraints belong to the screen that owns their behavior.
 */
object BillionBeersBrand {
  val screenTitle: TextStyle
    @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleLarge

  val catalogTitle: TextStyle
    @Composable
    @ReadOnlyComposable
    get() =
      MaterialTheme.typography.titleLarge.copy(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        fontFamily = FontFamily.Serif,
      )

  val beerTitle: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)

  val detailTitle: TextStyle
    @Composable
    @ReadOnlyComposable
    get() =
      MaterialTheme.typography.headlineLarge.copy(
        fontWeight = FontWeight.ExtraBold,
        fontFamily = FontFamily.Serif,
      )

  val compactDetailTitle: TextStyle
    @Composable
    @ReadOnlyComposable
    get() =
      MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        fontFamily = FontFamily.Serif,
      )

  val sectionTitle: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)

  val bodyStrong: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)

  val actionLabel: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)

  val badgeLabel: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)

  val metricLabel: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)

  val description: TextStyle
    @Composable
    @ReadOnlyComposable
    get() =
      MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
      )

  val iosDetailTitle: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)

  val imagePlaceholder: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)

  val detailHeroBackground = Color(HERO_BACKGROUND)
  val detailHeroForeground = Color(HERO_FOREGROUND)
  val detailHeroAccent = Color(HERO_ACCENT)
  val detailHeroIcon = Color.White
  val detailHeroAccentSize = DpSize(48.dp, 4.dp)
  val detailHeroHeight = 260.dp
  val largeTextDetailHeroHeight = 340.dp
  val detailArtworkSize = DpSize(112.dp, 200.dp)
  val beerArtworkSize = DpSize(72.dp, 96.dp)
  val beerCardElevation = 1.dp
}

private const val HERO_BACKGROUND = 0xFF302719
private const val HERO_FOREGROUND = 0xFFFFF7E6
private const val HERO_ACCENT = 0xFFE6AF64
