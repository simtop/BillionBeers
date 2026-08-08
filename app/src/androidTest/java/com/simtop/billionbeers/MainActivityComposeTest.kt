package com.simtop.billionbeers

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.Brewery
import com.simtop.billionbeers.di.BaseAppGraph
import com.simtop.billionbeers.di.FakeBeersRepositoryModule
import com.simtop.billionbeers.utils.browseScreen
import com.simtop.billionbeers.utils.detailScreen
import com.simtop.billionbeers.utils.homeScreen
import com.simtop.billionbeers.utils.runMainActivityTest
import com.simtop.core.core.Either
import dev.zacsweers.metro.createGraphFactory
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainActivityComposeTest {

  @get:Rule val composeTestRule = createEmptyComposeRule()

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

    FakeBeersRepositoryModule.fakeBeersRepository.setBeers(listOf(fakeBeer))
    FakeBeersRepositoryModule.fakeBeersRepository.beerStyles = Either.Right(listOf(fakeStyle))
    FakeBeersRepositoryModule.fakeBeersRepository.breweries = Either.Right(listOf(fakeBrewery))
    val testGraph =
      createGraphFactory<TestAppGraph.Factory>().create(context = context) as BaseAppGraph

    app.appGraph = testGraph
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
        clickToggleAvailability()
        waitUntilToggleButtonShowsRefillBarrels()
        assertToggleButtonShowsRefillBarrels()
        navigateBack()
      }

      homeScreen {
        waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
        assertBeerIsUnavailable(fakeBeer.name)
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
        waitUntilNodeWithTextIsDisplayed(fakeBrewery.name)
        assertBreweryIsDisplayed(fakeBrewery.name)

        pressBack()
      }

      homeScreen { waitUntilNodeWithTextIsDisplayed(fakeBeer.name) }
    }
}
