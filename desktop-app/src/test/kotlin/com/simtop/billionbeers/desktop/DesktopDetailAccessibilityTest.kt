package com.simtop.billionbeers.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.shared.beerdetail.SharedBeerDetailContent
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class DesktopDetailAccessibilityTest {
  @Test
  fun controlsRemainVisibleOverWhiteImagesAndTwoLineTitleHasRoom() =
    runSkikoComposeUiTest(size = Size(390f, 844f)) {
      val name = "Gypsy gyp Porter\nwith descending letters"
      setContent {
        SharedBeerDetailContent(
          beer = Beer.empty.copy(name = name),
          strings = desktopStrings.detailStrings,
          onBackClick = {},
          onToggleAvailability = {},
          onToggleFavorite = {},
          backIcon = desktopHost.backIcon,
          favoriteIcon = desktopHost.favoriteIcon,
          imageContent = { _, _, modifier -> Box(modifier.background(Color.White)) },
        )
      }
      for (label in listOf("Back", "Add to favorites")) {
        val button = onNodeWithContentDescription(label)
        button.assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val pixels = button.captureToImage().toPixelMap()
        val darkPixels =
          (0 until pixels.height).sumOf { y ->
            (0 until pixels.width).count { x -> pixels[x, y].red < 0.5f }
          }
        assertTrue("$label needs a contrasting backdrop", darkPixels > 500)
        val lightPixels =
          (0 until pixels.height).sumOf { y ->
            (0 until pixels.width).count { x ->
              val pixel = pixels[x, y]
              pixel.red > 0.8f && pixel.green > 0.8f && pixel.blue > 0.8f
            }
          }
        assertTrue("$label needs a visible light icon on the dark hero", lightPixels > 25)
      }
      val title = onNodeWithText(name).fetchSemanticsNode().boundsInRoot
      assertTrue("Title must have bottom space before the header ends", title.bottom < 350f)
      assertTrue("Two lines must fit", title.height > 60f)
      val file = File("build/qa/detail-white-image.png")
      file.parentFile.mkdirs()
      ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
    }

  @Test
  fun backAndFavoriteButtonsExposeLabelsRolesAndUpdatedState() = runSkikoComposeUiTest {
    var beer by mutableStateOf(Beer.empty.copy(name = "QA accessibility beer"))
    var backClicks = 0
    setContent {
      SharedBeerDetailContent(
        beer = beer,
        strings = desktopStrings.detailStrings,
        onBackClick = { backClicks++ },
        onToggleAvailability = {},
        onToggleFavorite = { beer = beer.copy(isFavorite = !beer.isFavorite) },
        backIcon = desktopHost.backIcon,
        favoriteIcon = desktopHost.favoriteIcon,
        imageContent = desktopHost.imageContent,
        animationsDisabled = true,
      )
    }

    onAllNodesWithContentDescription("Back").assertCountEquals(1)
    onNodeWithContentDescription("Back")
      .assertIsDisplayed()
      .assertHasClickAction()
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
      .performClick()
    runOnIdle { assertEquals(1, backClicks) }

    for (label in listOf("Add to favorites", "Remove from favorites")) {
      onAllNodesWithContentDescription(label).assertCountEquals(1)
      onNodeWithContentDescription(label)
        .assertIsDisplayed()
        .assertHasClickAction()
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, label))
        .performClick()
    }
    onNodeWithContentDescription("Add to favorites").assertIsDisplayed()
    runOnIdle { assertEquals(false, beer.isFavorite) }
  }
}
