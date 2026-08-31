package com.simtop.feature.beerbrowse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.Brewery
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListFooter
import com.simtop.core.core.PagedListUiModel
import com.simtop.feature.beerbrowse.presentation.BrowseBeersContent
import com.simtop.feature.beerbrowse.presentation.BrowseHomeContent
import com.simtop.presentation_utils.R
import com.simtop.testing_utils_android.BaseTestRobot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BeerBrowseUiTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val style = BeerStyle(id = "style-1", name = "IPA")
  private val brewery =
    Brewery(
      id = "brewery-1",
      name = "Hop Haven",
      countryCode = "BE",
      foundedYear = 1980,
      imageUrl = "",
    )
  private val beer = Beer.empty.copy(id = "beer-1", name = "Punk IPA")

  @Test
  fun selectingBrowseTabsAndItemsReportsTheUserSelection() {
    var clickedStyle: BeerStyle? = null
    var clickedBrewery: Brewery? = null

    composeTestRule.setContent {
      BillionBeersTheme {
        var selectedTab by remember { mutableIntStateOf(0) }
        BrowseHomeContent(
          styles = CommonUiState.Success(listOf(style)),
          breweries = CommonUiState.Success(listOf(brewery)),
          selectedTab = selectedTab,
          onTabSelected = { selectedTab = it },
          onStyleClick = { clickedStyle = it },
          onBreweryClick = { clickedBrewery = it },
          onBack = {},
          onRetryStyles = {},
          onRetryBreweries = {},
        )
      }
    }

    val robot = BaseTestRobot(composeTestRule)
    robot.assertTextIsDisplayed(style.name)
    robot.assertEveryClickableIsLabelled()
    composeTestRule.onNodeWithText(style.name).performClick()
    assertEquals(style, clickedStyle)

    composeTestRule.onNodeWithText(string(R.string.browse_tab_breweries)).performClick()
    composeTestRule.onNodeWithText(brewery.name).performClick()
    assertEquals(brewery, clickedBrewery)
    robot.assertEveryClickableIsLabelled()
  }

  @Test
  fun browseResultsExposeBeerClicksAndAnExplicitRetry() {
    var clickedBeer: Beer? = null
    var retryCount = 0

    composeTestRule.setContent {
      BillionBeersTheme {
        BrowseBeersContent(
          title = "IPA",
          viewState =
            CommonUiState.Success(
              PagedListUiModel(
                items = listOf(beer),
                totalCount = 1,
                footer = PagedListFooter.Retry,
              )
            ),
          onBack = {},
          onBeerClick = { clickedBeer = it },
          onScrollToBottom = {},
          onRetryLoadMore = { retryCount++ },
          onRetryFirstPage = {},
        )
      }
    }

    val robot = BaseTestRobot(composeTestRule)
    robot.assertTextIsDisplayed(beer.name)
    robot.assertEveryClickableIsLabelled()
    composeTestRule.onNodeWithText(beer.name).performClick()
    assertEquals(beer, clickedBeer)

    composeTestRule.onNodeWithText(string(R.string.retry)).performClick()
    assertEquals(1, retryCount)
  }

  private fun string(resId: Int): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)
}
