package com.simtop.billionbeers.shared.presentation

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A font-independent, 24dp Back affordance for hosts without a platform icon provider. Mirrors in
 * RTL, uses the host's content color, and exposes its localized description without glyph text.
 */
@Composable
fun SharedBackIcon(contentDescription: String, modifier: Modifier = Modifier) {
  Icon(BackArrow, contentDescription = contentDescription, modifier = modifier)
}

private val BackArrow =
  ImageVector.Builder(
      name = "Back",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
      autoMirror = true,
    )
    .apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(20f, 11f)
        horizontalLineTo(7.83f)
        lineTo(13.42f, 5.41f)
        lineTo(12f, 4f)
        lineTo(4f, 12f)
        lineTo(12f, 20f)
        lineTo(13.42f, 18.59f)
        lineTo(7.83f, 13f)
        horizontalLineTo(20f)
        close()
      }
    }
    .build()
