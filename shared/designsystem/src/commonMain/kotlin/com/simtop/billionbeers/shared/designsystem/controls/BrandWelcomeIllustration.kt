package com.simtop.billionbeers.shared.designsystem.controls

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme

@Composable
@Suppress("MagicNumber") // Coordinates and opacity values for the decorative vector artwork.
fun BrandWelcomeIllustration(modifier: Modifier = Modifier) {
  val palette = BillionBeersTheme.colorScheme
  Canvas(modifier.clearAndSetSemantics {}) {
    // Draw in a fixed coordinate space so artwork stays proportional in a short landscape window.
    val factor = minOf(size.width / 320f, size.height / 230f)
    translate((size.width - 320f * factor) / 2f, (size.height - 230f * factor) / 2f) {
      scale(factor, factor, pivot = Offset.Zero) {
        drawCircle(palette.primaryContainer.copy(alpha = 0.45f), 100f, Offset(160f, 118f))
        rotate(-9f, Offset(112f, 125f)) {
          beerCard(Offset(36f, 34f), palette.surface, palette.primaryContainer, palette.primary)
        }
        rotate(10f, Offset(222f, 125f)) {
          beerCard(Offset(158f, 43f), palette.surface, palette.primary, palette.onPrimary)
        }
        drawCircle(palette.primaryContainer, 26f, Offset(257f, 46f))
        val heart =
          Path().apply {
            moveTo(257f, 59f)
            cubicTo(226f, 40f, 247f, 23f, 257f, 38f)
            cubicTo(267f, 23f, 288f, 40f, 257f, 59f)
            close()
          }
        drawPath(heart, palette.primary)
        drawCircle(palette.primary.copy(alpha = 0.5f), 3f, Offset(38f, 27f))
        drawCircle(palette.primary.copy(alpha = 0.4f), 2f, Offset(283f, 184f))
      }
    }
  }
}

/**
 * Saved cards gathered into a collection, with the same visual language as the discovery artwork.
 */
@Composable
@Suppress("MagicNumber") // Coordinates and opacity values for the decorative vector artwork.
fun BrandCollectionIllustration(modifier: Modifier = Modifier) {
  val palette = BillionBeersTheme.colorScheme
  Canvas(modifier.clearAndSetSemantics {}) {
    val factor = minOf(size.width / 320f, size.height / 230f)
    translate((size.width - 320f * factor) / 2f, (size.height - 230f * factor) / 2f) {
      scale(factor, factor, pivot = Offset.Zero) {
        drawCircle(palette.primaryContainer.copy(alpha = 0.45f), 100f, Offset(160f, 118f))
        rotate(-12f, Offset(160f, 125f)) {
          beerCard(Offset(87f, 30f), palette.surfaceVariant, palette.primary, palette.onPrimary)
        }
        rotate(9f, Offset(160f, 125f)) {
          beerCard(Offset(113f, 40f), palette.surface, palette.primaryContainer, palette.primary)
        }
        drawCircle(palette.primaryContainer, 29f, Offset(236f, 168f))
        val heart =
          Path().apply {
            moveTo(236f, 182f)
            cubicTo(202f, 161f, 225f, 143f, 236f, 159f)
            cubicTo(247f, 143f, 270f, 161f, 236f, 182f)
            close()
          }
        drawPath(heart, palette.primary)
        drawCircle(palette.primary.copy(alpha = 0.5f), 3f, Offset(55f, 63f))
        drawCircle(palette.primary.copy(alpha = 0.4f), 2f, Offset(271f, 75f))
      }
    }
  }
}

@Suppress("MagicNumber") // Glass and paper geometry in the illustration's fixed coordinate space.
private fun DrawScope.beerCard(origin: Offset, paper: Color, beer: Color, foam: Color) {
  drawRoundRect(
    Color.Black.copy(alpha = 0.06f),
    origin + Offset(0f, 5f),
    Size(112f, 166f),
    CornerRadius(16f),
  )
  drawRoundRect(paper, origin, Size(112f, 166f), CornerRadius(16f))
  val glass =
    Path().apply {
      moveTo(origin.x + 30f, origin.y + 43f)
      lineTo(origin.x + 82f, origin.y + 43f)
      lineTo(origin.x + 74f, origin.y + 121f)
      quadraticTo(origin.x + 56f, origin.y + 131f, origin.x + 38f, origin.y + 121f)
      close()
    }
  drawPath(glass, beer)
  drawPath(glass, foam.copy(alpha = 0.25f), style = Stroke(2f))
  drawRoundRect(foam, origin + Offset(29f, 38f), Size(54f, 17f), CornerRadius(8f))
  drawLine(foam.copy(alpha = 0.45f), origin + Offset(44f, 67f), origin + Offset(48f, 108f), 4f)
  drawRoundRect(
    beer.copy(alpha = 0.3f),
    origin + Offset(36f, 145f),
    Size(40f, 3f),
    CornerRadius(2f),
  )
}
