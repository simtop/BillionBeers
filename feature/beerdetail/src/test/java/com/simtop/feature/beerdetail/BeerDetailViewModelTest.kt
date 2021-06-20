package com.simtop.feature.beerdetail

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.simtop.beerdomain.domain.usecases.UpdateAvailabilityUseCase
import com.simtop.billionbeers.testing_utils.*
import com.simtop.core.core.Either
import com.simtop.feature.beerdetail.presentation.BeerDetailViewModel
import com.simtop.feature.beerdetail.presentation.BeersDetailViewState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import strikt.api.expect
import strikt.assertions.isEqualTo

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

        expect {
            that(liveDataUnderTest.observedValues) {
                get { size }.isEqualTo(1)
                get { get(0) }.isEqualTo(
                    BeersDetailViewState.Success(
                        fakeBeerModel
                    )
                )
            }
        }
    }

    @Test
    fun `when usecase fails we get error state`() = coroutineScope.runBlocking {
        // Arrange

        coEvery {
            availabilityUseCase.execute(any())
        } returns Either.Left(fakeException)

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

        expect {
            that(liveDataUnderTest.observedValues) {
                get { size }.isEqualTo(3)
                get { get(2) }.isEqualTo(
                    BeersDetailViewState.Error(
                        fakeException.message
                    )
                )
            }
        }
    }

    @Test
    fun `when usecase succeeds we get success state with different availability`() =
        coroutineScope.runBlocking {
            // Arrange

            coEvery {
                availabilityUseCase.execute(any())
            } returns com.simtop.core.core.Either.Right(Unit)

            val testExpectedResponse =
                fakeBeerModel.copy(availability = !fakeBeerModel.availability)

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

            expect {
                that(liveDataUnderTest.observedValues) {
                    get { size }.isEqualTo(2)
                    get { get(0) }.isEqualTo(
                        BeersDetailViewState.Success(
                            testExpectedResponse
                        )
                    )
                }
            }
        }
}

