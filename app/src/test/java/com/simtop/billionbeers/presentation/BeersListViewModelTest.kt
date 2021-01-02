package com.simtop.billionbeers.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.simtop.billionbeers.*
import com.simtop.billionbeers.core.Either
import com.simtop.billionbeers.domain.usecases.GetAllBeersUseCase
import com.simtop.billionbeers.presentation.beerslist.BeersListViewModel
import com.simtop.billionbeers.presentation.beerslist.BeersListViewState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@ExperimentalCoroutinesApi
internal class BeersListViewModelTest {

    @get:Rule
    var rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineScope = MainCoroutineScopeRule()

    private val getAllBeersUseCase: GetAllBeersUseCase = mockk()

    @Test
    fun `when we get list of beer it succeeds and shows loader`() = coroutineScope.runBlocking {
        coEvery {
            getAllBeersUseCase.execute(any())
        } returns Either.Right(fakeBeerListModel)

        coroutineScope.dispatcher.pauseDispatcher()

        val beersListViewModel = BeersListViewModel(coroutineScope.testDispatcherProvider, getAllBeersUseCase)

        val liveDataUnderTest = beersListViewModel.beerListViewState.testObserver()

        coroutineScope.dispatcher.resumeDispatcher()

        coVerify(exactly = 1) {
            getAllBeersUseCase.execute(any())
        }

        liveDataUnderTest.observedValues.size shouldBeEqualTo 2
        liveDataUnderTest.observedValues[0] shouldBeEqualTo BeersListViewState.Loading
        liveDataUnderTest.observedValues[1] shouldBeEqualTo BeersListViewState.Success(
            fakeBeerListModel
        )
    }

    @Test
    fun `when we get list of beer it fails and shows error`() = coroutineScope.runBlocking {
        coEvery {
            getAllBeersUseCase.execute(any())
        } returns Either.Left(Exception("Error getting list of beers"))

        coroutineScope.dispatcher.pauseDispatcher()

        val beersListViewModel = BeersListViewModel(coroutineScope.testDispatcherProvider, getAllBeersUseCase)

        val liveDataUnderTest = beersListViewModel.beerListViewState.testObserver()

        coroutineScope.dispatcher.resumeDispatcher()

        coVerify(exactly = 1) {
            getAllBeersUseCase.execute(any())
        }

        liveDataUnderTest.observedValues.size shouldBeEqualTo 2
        liveDataUnderTest.observedValues[0] shouldBeEqualTo BeersListViewState.Loading
        liveDataUnderTest.observedValues[1] shouldBeEqualTo BeersListViewState.Error("Error getting list of beers")
    }
}