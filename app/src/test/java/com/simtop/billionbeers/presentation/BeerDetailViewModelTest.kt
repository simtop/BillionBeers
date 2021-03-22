package com.simtop.billionbeers.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.simtop.beerdomain.domain.usecases.UpdateAvailabilityUseCase
import com.simtop.billionbeers.*
import com.simtop.core.core.Either
import com.simtop.feature.beerdetail.presentation.BeerDetailViewModel
import com.simtop.feature.beerdetail.presentation.BeersDetailViewState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@ExperimentalCoroutinesApi
internal class BeerDetailViewModelTest {

    @get:Rule
    var rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineScope = MainCoroutineScopeRule()

    private val availabilityUseCase: UpdateAvailabilityUseCase = mockk()

    @Test
    fun `when creating viewmodel we get success state`() = coroutineScope.runBlocking {
        // Arrange

        // Act

        coroutineScope.dispatcher.pauseDispatcher()

        val beerDetailViewModel = BeerDetailViewModel(
            coroutineScope.testDispatcherProvider,
            availabilityUseCase,
            fakeBeerModel
        )

        val liveDataUnderTest = beerDetailViewModel.beerDetailViewState.testObserver()

        coroutineScope.dispatcher.resumeDispatcher()

        // Assert
        liveDataUnderTest.observedValues.size shouldBeEqualTo 1
        liveDataUnderTest.observedValues[0] shouldBeEqualTo BeersDetailViewState.Success(
            fakeBeerModel
        )
    }

    @Test
    fun `when usecase fails we get error state`() = coroutineScope.runBlocking {
        // Arrange

        coEvery {
            availabilityUseCase.execute(any())
        } returns com.simtop.core.core.Either.Left(fakeException)

        // Act

        coroutineScope.dispatcher.pauseDispatcher()

        val beerDetailViewModel = BeerDetailViewModel(
            coroutineScope.testDispatcherProvider,
            availabilityUseCase,
            fakeBeerModel
        )

        val liveDataUnderTest = beerDetailViewModel.beerDetailViewState.testObserver()

        coroutineScope.dispatcher.resumeDispatcher()

        beerDetailViewModel.updateAvailability(fakeBeerModel)

        // Assert

        liveDataUnderTest.observedValues.size shouldBeEqualTo 3
        liveDataUnderTest.observedValues[2] shouldBeEqualTo BeersDetailViewState.Error(
            fakeException.message
        )
    }

    @Test
    fun `when usecase succeeds we get success state with different availability`() = coroutineScope.runBlocking {
        // Arrange

        coEvery {
            availabilityUseCase.execute(any())
        } returns com.simtop.core.core.Either.Right(Unit)

        val testExpectedResponse = fakeBeerModel.copy(availability = !fakeBeerModel.availability)

        // Act

        coroutineScope.dispatcher.pauseDispatcher()

        val beerDetailViewModel = BeerDetailViewModel(
            coroutineScope.testDispatcherProvider,
            availabilityUseCase,
            fakeBeerModel
        )

        val liveDataUnderTest = beerDetailViewModel.beerDetailViewState.testObserver()

        coroutineScope.dispatcher.resumeDispatcher()
        beerDetailViewModel.updateAvailability(fakeBeerModel)


        // Assert
        liveDataUnderTest.observedValues.size shouldBeEqualTo 2
        liveDataUnderTest.observedValues[1] shouldBeEqualTo BeersDetailViewState.Success(
            testExpectedResponse
        )
    }
}

