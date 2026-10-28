package com.simtop.billionbeers

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
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
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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
import com.simtop.billionbeers.utils.useWithFailureScreenshot
import com.simtop.core.core.Either
import com.simtop.feature.beersearch.R as SearchR
import com.simtop.feature.beersearch.SEARCH_FIELD_TAG
import com.simtop.feature.beerslist.R as CatalogR
import com.simtop.navigation.DynamicFeature
import com.simtop.testing_utils_android.withFailureScreenshot
import dev.zacsweers.metro.createGraphFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainActivityComposeTest {

  private val composeTestRule = createEmptyComposeRule()
  @get:Rule val failureScreenshots = withFailureScreenshot(composeTestRule)
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
  fun welcomeSurvivesRecreationThenDismissesAcrossLaunches() {
    resetOnboarding()
    ActivityScenario.launch(MainActivity::class.java).useWithFailureScreenshot { scenario ->
      composeTestRule.onNodeWithContentDescription("Page 1 of 2").assertIsDisplayed()
      scenario.recreate()
      composeTestRule.onNodeWithText("Next").performClick()
      composeTestRule.onNodeWithText("Build your own collection.").assertIsDisplayed()
      assertWelcomePending()
      scenario.recreate()
      composeTestRule.onNodeWithContentDescription("Page 2 of 2").assertIsDisplayed()
      composeTestRule.onNodeWithText("Build your own collection.").assertIsDisplayed()
      composeTestRule.onNodeWithTag("introduction_back").performClick()
      composeTestRule.onNodeWithContentDescription("Page 1 of 2").assertIsDisplayed()
      composeTestRule.onNodeWithTag("introduction_pager").performTouchInput { swipeLeft() }
      composeTestRule.onNodeWithContentDescription("Page 2 of 2").assertIsDisplayed()
      pressBack()
      composeTestRule.onNodeWithContentDescription("Page 1 of 2").assertIsDisplayed()
      completeWelcome()
      composeTestRule.homeScreen { waitUntilNodeWithTextIsDisplayed(fakeBeer.name) }
      scenario.recreate()
      composeTestRule.onNodeWithTag("introduction_continue").assertDoesNotExist()
      composeTestRule.homeScreen { waitUntilNodeWithTextIsDisplayed(fakeBeer.name) }
    }
    ActivityScenario.launch(MainActivity::class.java).useWithFailureScreenshot {
      composeTestRule.homeScreen { waitUntilNodeWithTextIsDisplayed(fakeBeer.name) }
      composeTestRule.onNodeWithTag("introduction_continue").assertDoesNotExist()
    }
  }

  @Test
  fun welcomeCanBeSkippedFromEitherPage() {
    for (page in 0..1) {
      resetOnboarding()
      ActivityScenario.launch(MainActivity::class.java).useWithFailureScreenshot {
        if (page == 1) composeTestRule.onNodeWithText("Next").performClick()
        composeTestRule.onNodeWithTag("introduction_skip").performClick()
        composeTestRule.homeScreen { waitUntilNodeWithTextIsDisplayed(fakeBeer.name) }
      }
      ActivityScenario.launch(MainActivity::class.java).useWithFailureScreenshot {
        composeTestRule.homeScreen { waitUntilNodeWithTextIsDisplayed(fakeBeer.name) }
        composeTestRule.onNodeWithTag("introduction_continue").assertDoesNotExist()
      }
    }
  }

  @Test
  fun linkReceivedOnWelcomeOpensDestinationWithoutDismissingWelcomePermanently() {
    resetOnboarding()
    ActivityScenario.launch(MainActivity::class.java).useWithFailureScreenshot { scenario ->
      composeTestRule.onNodeWithText("Next").performClick()
      composeTestRule.onNodeWithContentDescription("Page 2 of 2").assertIsDisplayed()
      scenario.onActivity { activity ->
        // ActivityScenario ignores lifecycle events after setIntent changes the launch identity.
        // Deliver the warm callback directly and keep its original intent for recreation tracking.
        val launchIntent = activity.intent
        InstrumentationRegistry.getInstrumentation()
          .callActivityOnNewIntent(activity, deepLinkIntent("billionbeers://favorites"))
        activity.intent = launchIntent
      }
      assertSelectedTab("favorites_tab")
      scenario.recreate()
      assertSelectedTab("favorites_tab")
      composeTestRule.onNodeWithTag("introduction_continue").assertDoesNotExist()
      val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
          as BillionBeersApplication
      assertFalse(
        checkNotNull(app.appGraph.onboardingController.state.value)
          .isComplete(com.simtop.billionbeers.shared.presentation.OnboardingIntroductions.welcome)
      )
    }
    ActivityScenario.launch(MainActivity::class.java).useWithFailureScreenshot {
      composeTestRule.onNodeWithTag("introduction_continue").assertIsDisplayed()
    }
  }

  @Test
  fun firstDetailHintCanBeDismissedAndDoesNotFollowTheNextBeer() {
    resetOnboarding()
    runMainActivityTest(composeTestRule) {
      completeWelcome()
      homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        clickOnBeer(fakeBeer.name)
      }
      onNodeWithTag("introduction_hint").assertIsDisplayed()
      val gotIt =
        InstrumentationRegistry.getInstrumentation()
          .targetContext
          .getString(com.simtop.presentation_utils.R.string.onboarding_got_it)
      onNodeWithText(gotIt).performClick()
      onNodeWithTag("introduction_hint").assertDoesNotExist()
      detailScreen { navigateBack() }
      homeScreen {
        waitUntilNodeWithTextIsDisplayed(secondFakeBeer.name)
        clickOnBeer(secondFakeBeer.name)
      }
      detailScreen { waitUntilNodeWithTextIsDisplayed(secondFakeBeer.description) }
      onNodeWithTag("introduction_hint").assertDoesNotExist()
    }
  }

  @Test
  fun coldBeerLinkBypassesWelcomeAndFavoriteHint() {
    resetOnboarding()
    ActivityScenario.launch<MainActivity>(deepLinkIntent("billionbeers://beers/${fakeBeer.id}"))
      .useWithFailureScreenshot {
        composeTestRule.detailScreen { waitUntilNodeWithTextIsDisplayed(fakeBeer.description) }
        composeTestRule.onNodeWithTag("introduction_continue").assertDoesNotExist()
        composeTestRule.onNodeWithTag("introduction_hint").assertDoesNotExist()
      }
  }

  @Test
  fun favoriteHintRemainsAfterFailedSaveAndClosesAfterSuccessfulSave() {
    resetOnboarding()
    runMainActivityTest(composeTestRule) {
      completeWelcome()
      homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        clickOnBeer(fakeBeer.name)
      }
      onNodeWithTag("introduction_hint").assertIsDisplayed()
      val repository = FakeBeersRepositoryModule.fakeBeersRepository
      try {
        repository.setExceptionToThrow(java.io.IOException("save failed"))
        onNodeWithTag("toggle_favorite").performClick()
        val addFavorite =
          InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(com.simtop.presentation_utils.R.string.add_to_favorites)
        onNodeWithContentDescription(addFavorite).assertIsDisplayed()
        onNodeWithTag("introduction_hint").assertIsDisplayed()
        repository.setExceptionToThrow(null)
        onNodeWithTag("toggle_favorite").performClick()
        waitUntil(timeoutMillis = 5_000) {
          repository.getBeers().first { it.id == fakeBeer.id }.isFavorite
        }
        onNodeWithTag("introduction_hint").assertDoesNotExist()
      } finally {
        repository.setExceptionToThrow(null)
      }
    }
  }

  private fun completeWelcome() {
    composeTestRule.onNodeWithText("Next").performClick()
    composeTestRule.onNodeWithText("Build your own collection.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Explore beers").performClick()
  }

  private fun assertWelcomePending() {
    val app =
      InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        as BillionBeersApplication
    assertFalse(
      checkNotNull(app.appGraph.onboardingController.state.value)
        .isComplete(com.simtop.billionbeers.shared.presentation.OnboardingIntroductions.welcome)
    )
  }

  private fun resetOnboarding() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
          as BillionBeersApplication
      app.appGraph.onboardingController.reset()
    }
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
      pressKeyboardKey(KeyEvent.KEYCODE_ENTER)
      detailScreen { waitUntilNodeWithTextIsDisplayed(fakeBeer.description) }
      pressBack()
      onNodeWithTag(SEARCH_FIELD_TAG).assertIsDisplayed()
      tabToSearchResult()
      pressKeyboardKey(KeyEvent.KEYCODE_SPACE)
      detailScreen { waitUntilNodeWithTextIsDisplayed(fakeBeer.description) }
    }

  @Test
  fun inlineSearchCanClearToCatalogAndOpenAdvancedSearch() =
    runMainActivityTest(composeTestRule) {
      val context = InstrumentationRegistry.getInstrumentation().targetContext
      homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        clickOnSearch()
      }
      onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("Buzz")
      waitUntil(timeoutMillis = 5_000) {
        fakePagerFactory.createdQueries.lastOrNull()?.search == "Buzz"
      }
      onNodeWithContentDescription(context.getString(CatalogR.string.beers_clear_search))
        .performClick()
      homeScreen { waitUntilNodeWithTextIsDisplayed(secondFakeBeer.name) }
      onNodeWithContentDescription(context.getString(CatalogR.string.beers_advanced_search))
        .performClick()
      searchScreen {
        assertSearchFieldIsDisplayed()
        assertBackButtonIsDisplayed()
      }
      onNodeWithContentDescription(context.getString(SearchR.string.search_back)).performClick()
      homeScreen { assertCloseSearchIsDisplayed() }
      onNodeWithTag(SEARCH_FIELD_TAG).performImeAction()
      pressBack()
      onNodeWithTag(SEARCH_FIELD_TAG).assertDoesNotExist()
      homeScreen { assertBeerListIsDisplayed() }
    }

  @Test
  fun robotActionRecreatesActivityWhenScreenRotationChecksAreEnabled() {
    assumeTrue(
      "The monthly forward-compat lane enables this check",
      InstrumentationRegistry.getArguments().getString("UI_TEST_CHECK_SCREEN_ROTATION").toBoolean(),
    )

    ActivityScenario.launch(MainActivity::class.java).useWithFailureScreenshot { scenario ->
      lateinit var activityBeforeAction: MainActivity
      scenario.onActivity { activityBeforeAction = it }

      composeTestRule.homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        clickOnSearch()
      }

      lateinit var activityAfterAction: MainActivity
      scenario.onActivity { activityAfterAction = it }
      assertNotSame(
        "A robot action should recreate the Activity in the scheduled lane",
        activityBeforeAction,
        activityAfterAction,
      )
      composeTestRule.searchScreen { assertSearchFieldIsDisplayed() }
    }
  }

  private fun tabToSearchResult() {
    var reachedResult = false
    for (step in 1..20) {
      pressKeyboardKey(KeyEvent.KEYCODE_TAB)
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
          .onAllNodes(hasTestTag("beer_list_item") and hasText(fakeBeer.name) and isFocused())
          .fetchSemanticsNodes()
          .isNotEmpty()
      ) {
        reachedResult = true
        break
      }
    }
    assertTrue("Tab must reach a search result", reachedResult)
  }

  private fun pressKeyboardKey(keyCode: Int) {
    // Platform injection leaves touch mode; direct view dispatch can skip clickable focus targets.
    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode)
    composeTestRule.waitForIdle()
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
    ActivityScenario.launch<MainActivity>(link).useWithFailureScreenshot { scenario ->
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
    ActivityScenario.launch<MainActivity>(link).useWithFailureScreenshot { scenario ->
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
    ActivityScenario.launch<MainActivity>(link).useWithFailureScreenshot { scenario ->
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
    ActivityScenario.launch<MainActivity>(link).useWithFailureScreenshot {
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
        assertInteractiveNodesAreAccessible()
        clickOnBeer(fakeBeer.name)
      }

      detailScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.description)
        assertBeerDetailIsDisplayed(fakeBeer.name, fakeBeer.description)
        assertInteractiveNodesAreAccessible()
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
        assertInteractiveNodesAreAccessible()
        clickOnBeer(fakeBeer.name)
      }

      detailScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.description)
        assertInteractiveNodesAreAccessible()
        navigateBack()
      }

      homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        clickOnBrowse()
      }

      browseScreen {
        waitUntilNodeWithTextIsDisplayed(fakeStyle.name)
        assertInteractiveNodesAreAccessible()
        pressBack()
      }

      homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        clickOnSearch()
      }

      searchScreen {
        assertSearchFieldIsDisplayed()
        assertInteractiveNodesAreAccessible()
      }
      homeScreen { assertCloseSearchIsDisplayed() }
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
        assertInteractiveNodesAreAccessible()

        clickOnBreweriesTab()
        assertBreweriesTabIsSelected()
        waitUntilNodeWithTextIsDisplayed(fakeBrewery.name)
        assertBreweryIsDisplayed(fakeBrewery.name)

        pressBack()
      }

      homeScreen { waitUntilNodeWithTextIsDisplayed(fakeBeer.name) }
    }

  @Test
  fun systemBackFromBrowseSelectionReturnsToBrowseHome() =
    runMainActivityTest(composeTestRule) {
      homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        clickOnBrowse()
      }

      browseScreen {
        waitUntilNodeWithTextIsDisplayed(fakeStyle.name)
        clickOnStyle(fakeStyle.name)
        waitUntil(timeoutMillis = 5_000) {
          fakePagerFactory.createdQueries.lastOrNull()?.styleId == fakeStyle.id
        }
        runOnIdle { fakePagerFactory.searchPagers.last().setData(listOf(fakeBeer)) }
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)

        pressBack()
        assertStyleIsDisplayed(fakeStyle.name)
      }
    }
}
