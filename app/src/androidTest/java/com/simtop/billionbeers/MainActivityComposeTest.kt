package com.simtop.billionbeers

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.Brewery
import com.simtop.beerdomain.fakes.FakeBeersPagerFactory
import com.simtop.billionbeers.di.BaseAppGraph
import com.simtop.billionbeers.di.FakeBeersRepositoryModule
import com.simtop.billionbeers.fakes.FakeSplitInstallManager
import com.simtop.billionbeers.presentation.MainActivity
import com.simtop.billionbeers.utils.browseScreen
import com.simtop.billionbeers.utils.detailScreen
import com.simtop.billionbeers.utils.homeScreen
import com.simtop.billionbeers.utils.runMainActivityTest
import com.simtop.billionbeers.utils.searchScreen
import com.simtop.core.core.Either
import com.simtop.feature.beersearch.SEARCH_FIELD_TAG
import com.simtop.navigation.DynamicFeature
import dev.zacsweers.metro.createGraphFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainActivityComposeTest {

  @get:Rule val composeTestRule = createEmptyComposeRule()
  private lateinit var fakeSplitInstallManager: FakeSplitInstallManager
  private lateinit var fakePagerFactory: FakeBeersPagerFactory

  private val fakeBeer =
    Beer(
      id = "1",
      name = "Buzz",
      tagline = "A Real Bitter Experience.",
      description =
        "A light, crisp and bitter IPA brewed with English and American hops. A small batch brewed only once.",
      imageUrl = "https://images.punkapi.com/v2/keg.png",
      abv = 4.5,
      ibu = 60.0,
      foodPairing =
        listOf("Spicy chicken tikka masala", "Grilled chicken quesadilla", "Caramel toffee cake"),
      availability = true,
    )

  private val secondFakeBeer =
    fakeBeer.copy(
      id = "2",
      name = "Another Buzz",
      description = "A second beer used to verify detail replacement.",
    )

  private val fakeStyle = BeerStyle(id = "style-1", name = "IPA (Indian Pale Ale)")
  private val fakeBrewery =
    Brewery(
      id = "brewery-1",
      name = "Supreme Suds Collective",
      countryCode = "KP",
      foundedYear = 1972,
      imageUrl = "",
    )

  @Before
  fun setup() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    val app = context as BillionBeersApplication

    FakeBeersRepositoryModule.fakeBeersRepository.setBeers(listOf(fakeBeer, secondFakeBeer))
    FakeBeersRepositoryModule.fakeBeersRepository.beerStyles = Either.Right(listOf(fakeStyle))
    FakeBeersRepositoryModule.fakeBeersRepository.breweries = Either.Right(listOf(fakeBrewery))
    val testGraph =
      createGraphFactory<TestAppGraph.Factory>().create(context = context) as BaseAppGraph
    fakeSplitInstallManager =
      (testGraph.splitInstallManager as FakeSplitInstallManager).also { it.reset() }
    fakePagerFactory = testGraph.beersPagerFactory as FakeBeersPagerFactory

    app.activateAppGraph(testGraph)
  }

  @Test
  fun keyboardCanReachAndActivateASearchResult() =
    runMainActivityTest(composeTestRule) {
      homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        clickOnSearch()
      }
      onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("Buzz")
      waitUntil(timeoutMillis = 5_000) {
        fakePagerFactory.createdQueries.lastOrNull()?.search == "Buzz"
      }
      runOnIdle { fakePagerFactory.searchPagers.last().setData(listOf(fakeBeer)) }
      waitUntil(timeoutMillis = 5_000) {
        onAllNodes(hasTestTag("beer_list_item") and hasText(fakeBeer.name))
          .fetchSemanticsNodes()
          .isNotEmpty()
      }
      tabToSearchResult()
      onRoot().performKeyInput { pressKey(Key.Enter) }
      detailScreen { waitUntilNodeWithTextIsDisplayed(fakeBeer.description) }
      pressBack()
      onNodeWithTag(SEARCH_FIELD_TAG).assertIsDisplayed()
      tabToSearchResult()
      onRoot().performKeyInput { pressKey(Key.Spacebar) }
      detailScreen { waitUntilNodeWithTextIsDisplayed(fakeBeer.description) }
    }

  private fun tabToSearchResult() {
    var reachedResult = false
    for (step in 1..20) {
      composeTestRule.onRoot().performKeyInput { pressKey(Key.Tab) }
      val focused =
        composeTestRule.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
      val viewport = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
      focused.forEach {
        assertTrue(
          "Tab $step focused an offscreen control: ${it.config}",
          it.boundsInRoot.overlaps(viewport),
        )
      }
      if (
        composeTestRule
          .onAllNodes(hasText(fakeBeer.name) and isFocused())
          .fetchSemanticsNodes()
          .isNotEmpty()
      ) {
        reachedResult = true
        break
      }
    }
    assertTrue("Tab must reach a search result", reachedResult)
  }

  @Test
  fun debugDrawerRequiresRevealAndBackReturnsToContent() =
    runMainActivityTest(composeTestRule) {
      homeScreen { waitUntilNodeWithTextIsDisplayed(fakeBeer.name) }
      onRoot().performTouchInput { swipeRight(startX = 1f, endX = width - 1f) }
      onNodeWithText("Debug Drawer").assertDoesNotExist()
      val title =
        InstrumentationRegistry.getInstrumentation()
          .targetContext
          .getString(com.simtop.feature.beerslist.R.string.billion_beers_list)
      onNodeWithText(title).performTouchInput { longClick() }
      onNodeWithContentDescription("Open debug drawer").performClick()
      onNodeWithText("Network fault injection").assertIsDisplayed()
      pressBack()
      onNodeWithText("Debug Drawer").assertDoesNotExist()
      onNodeWithText(fakeBeer.name).assertIsDisplayed()
    }

  @Test
  fun repeatedFavoritesDeepLinkReturnsToFavoritesInTheSameActivity() {
    val link = deepLinkIntent("billionbeers://favorites")
    ActivityScenario.launch<MainActivity>(link).use { scenario ->
      lateinit var originalActivity: MainActivity
      scenario.onActivity { originalActivity = it }
      assertSelectedTab("favorites_tab")
      composeTestRule.onNodeWithTag("home_tab").performClick()
      assertSelectedTab("home_tab")

      scenario.onActivity { it.startActivity(Intent(link).setFlags(0)) }
      composeTestRule.waitForIdle()
      assertSelectedTab("favorites_tab")
      scenario.onActivity { assertSame(originalActivity, it) }
    }
  }

  @Test
  fun consumedDeepLinkDoesNotOverrideNavigationAfterRecreation() {
    val link = deepLinkIntent("billionbeers://favorites")
    ActivityScenario.launch<MainActivity>(link).use { scenario ->
      assertSelectedTab("favorites_tab")
      composeTestRule.onNodeWithTag("home_tab").performClick()
      assertSelectedTab("home_tab")

      scenario.recreate()
      composeTestRule.waitForIdle()
      assertSelectedTab("home_tab")

      scenario.onActivity { it.startActivity(Intent(link).setFlags(0)) }
      composeTestRule.waitForIdle()
      assertSelectedTab("favorites_tab")
    }
  }

  @Test
  fun repeatedCachedBeerDeepLinkReopensDetailAfterBack() {
    val link = deepLinkIntent("billionbeers://beers/${fakeBeer.id}")
    ActivityScenario.launch<MainActivity>(link).use { scenario ->
      composeTestRule.detailScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.description)
        navigateBack()
      }
      composeTestRule.homeScreen { waitUntilNodeWithTextIsDisplayed(fakeBeer.name) }

      scenario.onActivity { it.startActivity(Intent(link).setFlags(0)) }
      composeTestRule.detailScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.description)
        assertBeerDetailIsDisplayed(fakeBeer.name, fakeBeer.description)
        navigateBack()
      }
      assertSelectedTab("home_tab")
    }
  }

  @Test
  fun cachedBeerDeepLinkDoesNotBypassAMissingDetailSplit() {
    fakeSplitInstallManager.failInstallOf(DynamicFeature.BeerDetail.moduleName)
    val link = deepLinkIntent("billionbeers://beers/${fakeBeer.id}")
    ActivityScenario.launch<MainActivity>(link).use {
      val failureMessage =
        InstrumentationRegistry.getInstrumentation()
          .targetContext
          .getString(com.simtop.presentation_utils.R.string.failed_to_install_feature)
      composeTestRule.homeScreen { waitUntilNodeWithTextIsDisplayed(failureMessage) }
      composeTestRule.onNodeWithText(fakeBeer.description).assertDoesNotExist()
      assertEquals(
        listOf(DynamicFeature.BeerDetail.moduleName),
        fakeSplitInstallManager.requestedModules,
      )
    }
  }

  private fun deepLinkIntent(uri: String): Intent =
    Intent(
      Intent.ACTION_VIEW,
      Uri.parse(uri),
      InstrumentationRegistry.getInstrumentation().targetContext,
      MainActivity::class.java,
    )

  private fun assertSelectedTab(tag: String) {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodes(hasTestTag(tag) and isSelected()).fetchSemanticsNodes().size == 1
    }
    composeTestRule.onNodeWithTag(tag).assertIsSelected()
  }

  @Test
  fun shouldDisplayBeerListAndNavigateToDetail() =
    runMainActivityTest(composeTestRule) {
      homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        assertBeerNameIsDisplayed(fakeBeer.name)
        // Both real screens, checked where they are already composed - the catalog list and the
        // detail screen behind it. Cheaper here than a dedicated test per feature module, which
        // ADR 0009 prices at ~49s of module overhead against ~2s of test.
        assertEveryClickableIsLabelled()
        clickOnBeer(fakeBeer.name)
      }

      detailScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.description)
        assertBeerDetailIsDisplayed(fakeBeer.name, fakeBeer.description)
        assertEveryClickableIsLabelled()
      }
    }

  @Test
  fun togglingAvailabilityOnDetailScreenUpdatesHomeScreenAndSurvivesBackNavigation() =
    runMainActivityTest(composeTestRule) {
      homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        assertBeerIsAvailable(fakeBeer.name)
        clickOnBeer(fakeBeer.name)
      }

      detailScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.description)
        assertToggleButtonShowsMarkAsEmpty()
        assertAvailabilityActionSemantics(com.simtop.presentation_utils.R.string.beer_available)
        clickToggleAvailability()
        waitUntilToggleButtonShowsRefillBarrels()
        assertToggleButtonShowsRefillBarrels()
        assertAvailabilityActionSemantics(com.simtop.presentation_utils.R.string.beer_out_of_stock)
        navigateBack()
      }

      homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        assertBeerIsUnavailable(fakeBeer.name)
      }
    }

  @Test
  fun expandedListDetailKeepsTheListVisibleAfterSelection() {
    val widthDp =
      InstrumentationRegistry.getInstrumentation()
        .targetContext
        .resources
        .configuration
        .screenWidthDp
    assumeTrue(
      "Requires an expanded-width device",
      widthDp >= WIDTH_DP_EXPANDED_LOWER_BOUND,
    )

    runMainActivityTest(composeTestRule) {
      homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        clickOnBeer(fakeBeer.name)
        assertBeerListIsDisplayed()
      }

      detailScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.description)
        assertBeerDescriptionIsDisplayed(fakeBeer.description)
      }
    }
  }

  @Test
  fun selectingAnotherBeerReplacesDetailAndBackReturnsToList() =
    runMainActivityTest(composeTestRule) {
      homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        clickOnBeer(fakeBeer.name)
      }

      detailScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.description)
        navigateBack()
      }

      homeScreen {
        waitUntilNodeWithTextIsDisplayed(secondFakeBeer.name)
        clickOnBeer(secondFakeBeer.name)
      }

      detailScreen {
        waitUntilNodeWithTextIsDisplayed(secondFakeBeer.description)
        assertBeerDetailIsDisplayed(secondFakeBeer.name, secondFakeBeer.description)
        navigateBack()
      }

      homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        assertBeerNameIsDisplayed(secondFakeBeer.name)
      }
    }

  @Test
  fun representativeJourneysKeepInteractiveControlsAccessible() =
    runMainActivityTest(composeTestRule) {
      homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        assertEveryClickableIsLabelled()
        clickOnBeer(fakeBeer.name)
      }

      detailScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.description)
        assertEveryClickableIsLabelled()
        navigateBack()
      }

      homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        clickOnBrowse()
      }

      browseScreen {
        waitUntilNodeWithTextIsDisplayed(fakeStyle.name)
        assertEveryClickableIsLabelled()
        pressBack()
      }

      homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        clickOnSearch()
      }

      searchScreen {
        assertSearchFieldIsDisplayed()
        assertBackButtonIsDisplayed()
        assertEveryClickableIsLabelled()
      }
    }

  // The §10.7 proof: the browse destination lives in the *second* on-demand module, reached
  // through the same install gate as beerdetail (the fake installer reports it installed, so the
  // gate passes straight through to navigation - the dialog flow itself can't run without Play).
  @Test
  fun browseOpensTheDynamicModuleAndListsStylesAndBreweries() =
    runMainActivityTest(composeTestRule) {
      homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        clickOnBrowse()
      }

      browseScreen {
        waitUntilNodeWithTextIsDisplayed(fakeStyle.name)
        assertBrowseTitleIsDisplayed()
        assertStyleIsDisplayed(fakeStyle.name)
        // The only a11y coverage the browse dynamic feature gets - and its tabs are exactly the
        // kind of icon-adjacent control where an unlabelled clickable hides.
        assertEveryClickableIsLabelled()

        clickOnBreweriesTab()
        assertBreweriesTabIsSelected()
        waitUntilNodeWithTextIsDisplayed(fakeBrewery.name)
        assertBreweryIsDisplayed(fakeBrewery.name)

        pressBack()
      }

      homeScreen { waitUntilNodeWithTextIsDisplayed(fakeBeer.name) }
    }
}
