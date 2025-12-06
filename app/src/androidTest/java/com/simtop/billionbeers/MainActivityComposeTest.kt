package com.simtop.billionbeers

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.simtop.beer_data.di.BeersRepositoryModule
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.billionbeers.di.SplitInstallModule
import com.simtop.billionbeers.presentation.MainActivity
import com.simtop.billionbeers.robots.detailScreen
import com.simtop.billionbeers.robots.homeScreen
import com.simtop.navigation.FeatureConstants
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
@UninstallModules(BeersRepositoryModule::class, SplitInstallModule::class)
class MainActivityComposeTest {

  @get:Rule(order = 0) var hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val composeTestRule = createAndroidComposeRule<MainActivity>()

  @BindValue @JvmField val fakeBeersRepository: BeersRepository = FakeBeersRepository()

  @BindValue @JvmField val fakeSplitInstallManager: SplitInstallManager = mockk()

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

  @Before
  fun setup() {
    (fakeBeersRepository as FakeBeersRepository).setBeers(listOf(fakeBeer))

    every { fakeSplitInstallManager.installedModules } returns
      setOf(FeatureConstants.BEER_DETAIL_MODULE)
    every { fakeSplitInstallManager.registerListener(any()) } returns Unit
    every { fakeSplitInstallManager.unregisterListener(any()) } returns Unit
    // Not needed if relaxed=true, but good to be explicit if logic depends on it.
    every { fakeSplitInstallManager.startInstall(any()) } returns Tasks.forResult(0)

    hiltRule.inject()
  }

  @Test
  fun shouldDisplayBeerListAndNavigateToDetail() {
    homeScreen(composeTestRule) {
      waitUntilNodeWithTextIsDisplayed(fakeBeer.name)
      assertBeerNameIsDisplayed(fakeBeer.name)
      clickOnBeer(fakeBeer.name)
    }

    detailScreen(composeTestRule) {
      waitUntilNodeWithTextIsDisplayed(fakeBeer.description)
      assertBeerDetailIsDisplayed(fakeBeer.name, fakeBeer.description)
    }
  }
}
