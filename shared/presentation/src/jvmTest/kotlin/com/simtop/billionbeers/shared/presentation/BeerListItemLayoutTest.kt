package com.simtop.billionbeers.shared.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BeerListItemLayoutTest {
  @Test
  fun compactLargeTextKeepsMetricsAndBothStatusesReadable() {
    for (dark in listOf(false, true)) {
      checkRow(dark, fontScale = 2f, direction = LayoutDirection.Ltr)
    }
  }

  @Test
  fun compactLargeTextKeepsMetricsAndBothStatusesReadableInRtl() {
    checkRow(dark = true, fontScale = 2f, direction = LayoutDirection.Rtl)
  }

  @Test
  fun normalTextKeepsTheHostImageSlotAndRowActionUsable() {
    checkRow(dark = false, fontScale = 1f, direction = LayoutDirection.Ltr)
  }

  @Test
  fun webTitlePolicyKeepsLongBeerNamesCompleteAtDoubleTextSize() {
    checkRow(
      dark = false,
      fontScale = 2f,
      direction = LayoutDirection.Ltr,
      titleMaxLines = Int.MAX_VALUE,
    )
  }

  private fun checkRow(
    dark: Boolean,
    fontScale: Float,
    direction: LayoutDirection,
    titleMaxLines: Int = 1,
  ) =
    runSkikoComposeUiTest(size = Size(360f, 640f), density = Density(1f, fontScale)) {
      var beer by
        mutableStateOf(
          Beer.empty.copy(
            name = "Bière du catalogue",
            tagline = "Une bière de caractère.",
            availability = true,
          )
        )
      var clicks = 0
      setContent {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
          BillionBeersTheme(darkTheme = dark) {
            SharedBeerListItem(
              beer = beer,
              labels =
                BeerListItemLabels(
                  "ABV: 7.0%",
                  "IBU: 71.0",
                  if (beer.availability) "Disponible" else "Rupture de stock",
                ),
              onClick = { clicks++ },
              imageContent = { modifier -> Box(modifier.testTag("host_image")) },
              titleMaxLines = titleMaxLines,
            )
          }
        }
      }

      onNodeWithTag("host_image", useUnmergedTree = true)
        .assertWidthIsEqualTo(80.dp)
        .assertHeightIsEqualTo(80.dp)
      if (titleMaxLines == Int.MAX_VALUE) {
        onNodeWithText(beer.name, useUnmergedTree = true)
          .assertIsDisplayed()
          .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getResults ->
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            getResults(results)
            val layout = results.single()
            assertFalse(layout.didOverflowHeight)
            for (line in 0 until layout.lineCount) assertFalse(layout.isLineEllipsized(line))
            assertEquals(beer.name.length, layout.getLineEnd(layout.lineCount - 1))
          }
      }
      for (status in listOf("Disponible", "Rupture de stock")) {
        for (label in listOf("ABV: 7.0%", "IBU: 71.0", status)) {
          onNodeWithText(label, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertWidthIsAtLeast((30 * fontScale).dp)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getResults ->
              val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
              getResults(results)
              assertEquals(1, results.size)
              val layout = results.single()
              assertFalse(layout.didOverflowHeight, "Chip height must fit: $label")
              for (line in 0 until layout.lineCount) {
                assertFalse(layout.isLineEllipsized(line), "Chip copy must remain complete: $label")
                val textWidth = layout.getLineRight(line) - layout.getLineLeft(line)
                assertTrue(textWidth <= layout.size.width + 1f, "Chip glyphs must fit: $label")
              }
            }
        }
        val row = onNodeWithTag("beer_list_item")
        row
          .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
          .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, status))
          .performClick()
        System.getenv("BEER_ROW_QA_DIR")?.let { directory ->
          val file = File(directory, "row-$dark-$fontScale-$direction-$titleMaxLines-$status.png")
          file.parentFile.mkdirs()
          ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
        }
        runOnIdle { beer = beer.copy(availability = false) }
      }
      runOnIdle { assertEquals(2, clicks) }
    }
}
