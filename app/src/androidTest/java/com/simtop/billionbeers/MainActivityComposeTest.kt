package com.simtop.billionbeers

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.billionbeers.di.BaseAppGraph
import com.simtop.billionbeers.fakes.FakeSplitInstallManager
import com.simtop.billionbeers.utils.detailScreen
import com.simtop.billionbeers.utils.homeScreen
import com.simtop.billionbeers.utils.runMainActivityTest
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
      availability = true
    )

  private val fakeBeersRepository: BeersRepository = FakeBeersRepository(listOf(fakeBeer))

  private val fakeSplitInstallManager: SplitInstallManager = FakeSplitInstallManager()

  @Before
  fun setup() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    val app = context as BillionBeersApplication
    
    val testGraph = dev.zacsweers.metro.createGraphFactory<TestAppGraph.Factory>().create(
        context = context,
        beersRepository = fakeBeersRepository,
        splitInstallManager = fakeSplitInstallManager
    ) as BaseAppGraph
    
    app.appGraph = testGraph
  }

  @Test
  fun shouldDisplayBeerListAndNavigateToDetail() = runMainActivityTest(composeTestRule) {
    homeScreen {
      waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
      assertBeerNameIsDisplayed(fakeBeer.name)
      clickOnBeer(fakeBeer.name)
    }

    detailScreen {
      waitUntilNodeWithTextIsDisplayed(fakeBeer.description)
      assertBeerDetailIsDisplayed(fakeBeer.name, fakeBeer.description)
    }
  }
}
