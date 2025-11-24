package com.simtop.feature.beerdetail

import app.cash.turbine.test
import com.simtop.beerdomain.domain.usecases.UpdateAvailabilityUseCase
import com.simtop.billionbeers.testing_utils.fakeBeerModel
import com.simtop.billionbeers.testing_utils.fakeException
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.Either
import com.simtop.feature.beerdetail.presentation.BeerDetailViewModel
import com.simtop.feature.beerdetail.presentation.BeersDetailViewState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

@ExperimentalCoroutinesApi
internal class BeerDetailViewModelTest {

    private val coroutineDispatcherProvider = mockk<CoroutineDispatcherProvider>()
    private val availabilityUseCase = mockk<UpdateAvailabilityUseCase>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { coroutineDispatcherProvider.io } returns testDispatcher
        every { coroutineDispatcherProvider.main } returns testDispatcher
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when creating viewmodel we get success state`() = runTest(testDispatcher) {
        // Arrange & Act
        val beerDetailViewModel = BeerDetailViewModel(
            coroutineDispatcherProvider,
            availabilityUseCase,
            fakeBeerModel
        )

        // Assert
        beerDetailViewModel.beerDetailViewState.test {
            val item = awaitItem()
            expectThat(item).isA<BeersDetailViewState.Success>()
            expectThat((item as BeersDetailViewState.Success).result).isEqualTo(fakeBeerModel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when usecase fails we get error state`() = runTest(testDispatcher) {
        // Arrange
        coEvery {
            availabilityUseCase.execute(any())
        } returns Either.Left(fakeException)

        val beerDetailViewModel = BeerDetailViewModel(
            coroutineDispatcherProvider,
            availabilityUseCase,
            fakeBeerModel
        )

        // Act
        beerDetailViewModel.beerDetailViewState.test {
            // Initial success state
            expectThat(awaitItem()).isA<BeersDetailViewState.Success>()

            beerDetailViewModel.updateAvailability(fakeBeerModel)

            // Optimistic update
            expectThat(awaitItem()).isA<BeersDetailViewState.Success>()

            // Error state
            val errorItem = awaitItem()
            expectThat(errorItem).isA<BeersDetailViewState.Error>()
            expectThat((errorItem as BeersDetailViewState.Error).result).isEqualTo(fakeException.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when usecase succeeds we get success state with different availability`() = runTest(testDispatcher) {
        // Arrange
        coEvery {
            availabilityUseCase.execute(any())
        } returns Either.Right(Unit)

        val testExpectedResponse = fakeBeerModel.copy(availability = !fakeBeerModel.availability)

        val beerDetailViewModel = BeerDetailViewModel(
            coroutineDispatcherProvider,
            availabilityUseCase,
            fakeBeerModel
        )

        // Act
        beerDetailViewModel.beerDetailViewState.test {
            // Initial success state
            expectThat(awaitItem()).isA<BeersDetailViewState.Success>()

            beerDetailViewModel.updateAvailability(fakeBeerModel)

            // Optimistic update (toggled availability)
            val updatedItem = awaitItem()
            expectThat(updatedItem).isA<BeersDetailViewState.Success>()
            expectThat((updatedItem as BeersDetailViewState.Success).result.availability)
                .isEqualTo(testExpectedResponse.availability)

            // Since usecase succeeds, we don't expect another emission because the optimistic update was correct
            // and the usecase success doesn't trigger a new state emission in the current implementation
            // (it only emits on error).
            // However, if the usecase success *did* emit something, we'd check it here.
            // In the current VM implementation:
            // availabilityUseCase.execute(...).also(::treatResponse)
            // treatResponse only handles Left (Error). Right does nothing.
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}
