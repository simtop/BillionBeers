package com.simtop.billionbeers.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.simtop.beerdomain.domain.usecases.GetAllBeersUseCase
import com.simtop.billionbeers.testing_utils.*
import com.simtop.core.core.Either
import com.simtop.feature.beerslist.BeersListViewModel
import com.simtop.feature.beerslist.BeersListViewState
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import strikt.api.expect
import strikt.assertions.isEqualTo

@ExperimentalCoroutinesApi
internal class BeersListViewModelTest {

    @get:Rule
    var rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineScope = MainCoroutineScopeRule()

    private val getAllBeersUseCase: GetAllBeersUseCase = mockk()

    @Test
    fun `when usecase succeeds we get success state`() = coroutineScope.runBlocking {
        // Arrange

        val captureSlot = slot<GetAllBeersUseCase.Params>()

        coEvery {
            getAllBeersUseCase.execute(capture(captureSlot))
        } returns Either.Right(fakeBeerListModel)

        // Act

        coroutineScope.dispatcher.pauseDispatcher()

        val beersListViewModel =
            BeersListViewModel(coroutineScope.testDispatcherProvider, getAllBeersUseCase)

        val liveDataUnderTest = beersListViewModel.beerListViewState.testObserver()

        coroutineScope.dispatcher.resumeDispatcher()

        // Assert

        expect {
            that(captureSlot) {
                get { captured.quantity }.isEqualTo(4)
            }
            that(liveDataUnderTest.observedValues) {
                get { size }.isEqualTo(2)
                get { get(0) }.isEqualTo(BeersListViewState.Loading)
                get { get(1) }.isEqualTo(
                    BeersListViewState.Success(
                        fakeBeerListModel
                    )
                )
            }
        }
    }

    @Test
    fun `when usecase fails we get error state`() = coroutineScope.runBlocking {
        // Arrange

        val captureSlot = slot<GetAllBeersUseCase.Params>()

        coEvery {
            getAllBeersUseCase.execute(capture(captureSlot))
        } returns Either.Left(fakeException)

        // Act

        coroutineScope.dispatcher.pauseDispatcher()

        val beersListViewModel =
            BeersListViewModel(coroutineScope.testDispatcherProvider, getAllBeersUseCase)

        val liveDataUnderTest = beersListViewModel.beerListViewState.testObserver()

        coroutineScope.dispatcher.resumeDispatcher()

        // Assert

        expect {
            that(captureSlot) {
                get { captured.quantity }.isEqualTo(4)
            }
            that(liveDataUnderTest.observedValues) {
                get { size }.isEqualTo(2)
                get { get(0) }.isEqualTo(BeersListViewState.Loading)
                get { get(1) }.isEqualTo(
                    BeersListViewState.Error(fakeErrorName)
                )
            }
        }

    }
}