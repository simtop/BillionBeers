package com.simtop.billionbeers.shared.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BackIconRenderTest {
  @Test
  fun backRendersAnArrowWithoutTextAndMirrorsAtBothFontScales() {
    for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
      for (scale in listOf(1f, 2f)) {
        runSkikoComposeUiTest(size = Size(24f, 24f), density = Density(1f, scale)) {
          setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
              Surface(color = Color.White, contentColor = Color.Black) { SharedBackIcon("Back") }
            }
          }
          val icon = onNodeWithContentDescription("Back")
          icon
            .assertWidthIsEqualTo(24.dp)
            .assertHeightIsEqualTo(24.dp)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
          val pixels = icon.captureToImage().toPixelMap()
          // Above the shaft, an arrowhead has ink only on the leading side. A missing-glyph
          // rectangle, empty image, wrong direction or font-scaled text cannot satisfy this.
          // Exclude the two center columns where the arrowhead meets the stem.
          val left = (4..9).sumOf { y -> (0..10).count { x -> pixels[x, y].red < 0.5f } }
          val right = (4..9).sumOf { y -> (13..23).count { x -> pixels[x, y].red < 0.5f } }
          val head = if (direction == LayoutDirection.Ltr) left else right
          val tail = if (direction == LayoutDirection.Ltr) right else left
          assertTrue(head > 8, "Visible arrowhead: $direction, font scale $scale")
          assertEquals(0, tail, "Arrow must point toward Back: $direction")
          assertTrue(pixels[16, 11].red < 0.5f, "Visible horizontal shaft")
        }
      }
    }
  }
}
