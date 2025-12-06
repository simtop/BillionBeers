package com.simtop.feature.beerdetail

import app.cash.turbine.test
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.usecases.UpdateAvailabilityUseCase
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.billionbeers.testing_utils.fakeBeerModel
import com.simtop.billionbeers.testing_utils.fakeException
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.feature.beerdetail.presentation.BeerDetailViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

@ExperimentalCoroutinesApi
internal class BeerDetailViewModelTest {

  private val coroutineDispatcherProvider = mockk<CoroutineDispatcherProvider>()
  private val fakeBeersRepository = FakeBeersRepository()
  private val availabilityUseCase = UpdateAvailabilityUseCase(fakeBeersRepository)
  private val testDispatcher = StandardTestDispatcher()

  @BeforeEach
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    every { coroutineDispatcherProvider.io } returns testDispatcher
    every { coroutineDispatcherProvider.main } returns testDispatcher
  }

  @AfterEach
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `when creating viewmodel we get success state`() =
    runTest(testDispatcher) {
      // Arrange & Act
      val beerDetailViewModel =
        BeerDetailViewModel(coroutineDispatcherProvider, availabilityUseCase, fakeBeerModel)

      // Assert
      beerDetailViewModel.beerDetailViewState.test {
        val item = awaitItem()
        expectThat(item).isA<CommonUiState.Success<Beer>>()
        expectThat((item as CommonUiState.Success).data).isEqualTo(fakeBeerModel)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `when usecase fails we get error state`() =
    runTest(testDispatcher) {
      // Arrange
      fakeBeersRepository.setExceptionToThrow(fakeException)

      val beerDetailViewModel =
        BeerDetailViewModel(coroutineDispatcherProvider, availabilityUseCase, fakeBeerModel)

      // Act
      beerDetailViewModel.beerDetailViewState.test {
        // Initial success state
        expectThat(awaitItem()).isA<CommonUiState.Success<Beer>>()

        beerDetailViewModel.updateAvailability(fakeBeerModel)

        // Optimistic update
        expectThat(awaitItem()).isA<CommonUiState.Success<Beer>>()

        // Error state
        val errorItem = awaitItem()
        expectThat(errorItem).isA<CommonUiState.Error>()
        expectThat((errorItem as CommonUiState.Error).message).isEqualTo(fakeException.message)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `when usecase succeeds we get success state with different availability`() =
    runTest(testDispatcher) {
      // Arrange
      fakeBeersRepository.setExceptionToThrow(null)
      fakeBeersRepository.setBeers(listOf(fakeBeerModel))

      val testExpectedResponse = fakeBeerModel.copy(availability = !fakeBeerModel.availability)

      val beerDetailViewModel =
        BeerDetailViewModel(coroutineDispatcherProvider, availabilityUseCase, fakeBeerModel)

      // Act
      beerDetailViewModel.beerDetailViewState.test {
        // Initial success state
        expectThat(awaitItem()).isA<CommonUiState.Success<Beer>>()

        beerDetailViewModel.updateAvailability(fakeBeerModel)

        // Optimistic update (toggled availability)
        val updatedItem = awaitItem()
        expectThat(updatedItem).isA<CommonUiState.Success<Beer>>()
        expectThat((updatedItem as CommonUiState.Success).data.availability)
          .isEqualTo(testExpectedResponse.availability)

        // Since usecase succeeds, we don't expect another emission because the optimistic update
        // was correct
        // and the usecase success doesn't trigger a new state emission in the current
        // implementation
        // (it only emits on error).

        val storedBeer = fakeBeersRepository.getBeers().first()
        expectThat(storedBeer.availability).isEqualTo(testExpectedResponse.availability)

        cancelAndIgnoreRemainingEvents()
      }
    }
}
