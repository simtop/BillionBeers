package com.simtop.feature.beerbrowse

import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.Brewery
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.Either
import com.simtop.feature.beerbrowse.presentation.BrowseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

@ExperimentalCoroutinesApi
class BrowseViewModelTest {

  private val fakeRepository = FakeBeersRepository()

  private lateinit var testDispatcher: TestDispatcher

  @BeforeEach
  fun setUp() {
    testDispatcher = UnconfinedTestDispatcher()
    Dispatchers.setMain(testDispatcher)
  }

  @AfterEach fun tearDown() = Dispatchers.resetMain()

  private fun buildViewModel() = BrowseViewModel(fakeRepository)

  @Test
  fun `styles load on start and expose Success`() =
    runTest(testDispatcher) {
      val styles = listOf(BeerStyle(id = "1", name = "IPA"))
      fakeRepository.beerStyles = Either.Right(styles)

      val viewModel = buildViewModel()
      runCurrent()

      expectThat(viewModel.styles.value).isEqualTo(CommonUiState.Success(styles))
    }

  @Test
  fun `a failed styles load exposes the classified error and retry re-fetches`() =
    runTest(testDispatcher) {
      fakeRepository.beerStyles = Either.Left(FetchBeersError.Network)

      val viewModel = buildViewModel()
      runCurrent()

      expectThat(viewModel.styles.value).isA<CommonUiState.Error>()

      fakeRepository.beerStyles = Either.Right(listOf(BeerStyle(id = "1", name = "IPA")))
      viewModel.retryStyles()
      runCurrent()

      expectThat(viewModel.styles.value).isA<CommonUiState.Success<List<BeerStyle>>>()
    }

  @Test
  fun `breweries are not fetched until their tab is selected`() =
    runTest(testDispatcher) {
      val viewModel = buildViewModel()
      runCurrent()

      expectThat(fakeRepository.breweriesCallCount).isEqualTo(0)
      expectThat(viewModel.breweries.value).isEqualTo(CommonUiState.Loading)

      val breweries =
        listOf(
          Brewery(
            id = "1",
            name = "Hop Haven",
            countryCode = "BE",
            foundedYear = 1898,
            imageUrl = "",
          )
        )
      fakeRepository.breweries = Either.Right(breweries)
      viewModel.onBreweriesTabSelected()
      runCurrent()

      expectThat(viewModel.breweries.value).isEqualTo(CommonUiState.Success(breweries))
    }

  @Test
  fun `re-selecting the breweries tab does not re-fetch`() =
    runTest(testDispatcher) {
      fakeRepository.breweries = Either.Right(emptyList())
      val viewModel = buildViewModel()

      viewModel.onBreweriesTabSelected()
      viewModel.onBreweriesTabSelected()
      runCurrent()

      expectThat(fakeRepository.breweriesCallCount).isEqualTo(1)
    }

  @Test
  fun `an empty brewery list exposes Empty`() =
    runTest(testDispatcher) {
      fakeRepository.breweries = Either.Right(emptyList())
      val viewModel = buildViewModel()

      viewModel.onBreweriesTabSelected()
      runCurrent()

      expectThat(viewModel.breweries.value).isEqualTo(CommonUiState.Empty)
    }
}
