package com.simtop.billionbeers.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.shared.beerdetail.SharedBeerDetailContent
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class DesktopDetailAccessibilityTest {
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
        collapsingToolbarEnabled = desktopHost.detailCollapsingToolbarEnabled,
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
