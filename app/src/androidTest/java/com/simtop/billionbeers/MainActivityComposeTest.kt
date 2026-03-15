package com.simtop.billionbeers

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.billionbeers.di.AppGraph
import com.simtop.billionbeers.presentation.MainActivity
import com.simtop.billionbeers.robots.detailScreen
import com.simtop.billionbeers.robots.homeScreen
import com.simtop.core.core.DefaultCoroutineDispatcherProvider
import com.simtop.core.di.ViewModelFactory
import com.simtop.feature.beerslist.BeersListViewModel
import com.simtop.navigation.FeatureConstants
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainActivityComposeTest {

  @get:Rule val composeTestRule = createEmptyComposeRule()

  private val fakeBeersRepository: BeersRepository = FakeBeersRepository()

  private val fakeSplitInstallManager: SplitInstallManager = mockk()

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
    every { fakeSplitInstallManager.startInstall(any()) } returns Tasks.forResult(0)

    val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BillionBeersApplication
    
    val localViewModelFactory = ViewModelFactory(
        mapOf(
            BeersListViewModel::class to javax.inject.Provider<ViewModel> {
                BeersListViewModel(
                    DefaultCoroutineDispatcherProvider(),
                    com.simtop.beerdomain.domain.GetAllBeersUseCase(fakeBeersRepository),
                    com.simtop.beerdomain.domain.usecases.ObservePagingStateUseCase(fakeBeersRepository),
                    com.simtop.beerdomain.domain.usecases.LoadNextPageUseCase(fakeBeersRepository),
                    com.simtop.beerdomain.domain.usecases.RefreshBeersUseCase(fakeBeersRepository)
                )
            }
        )
    )
    
    val testGraph = mockk<AppGraph>(relaxed = true) {
        every { coroutineDispatcher } returns DefaultCoroutineDispatcherProvider()
        every { useCase } returns com.simtop.beerdomain.domain.usecases.UpdateAvailabilityUseCase(fakeBeersRepository)
        every { splitInstallManager } returns fakeSplitInstallManager
        every { viewModelFactory } returns localViewModelFactory
    }
    app.appGraph = testGraph
  }

  @Test
  fun shouldDisplayBeerListAndNavigateToDetail() {
    ActivityScenario.launch(MainActivity::class.java).use {
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
}
