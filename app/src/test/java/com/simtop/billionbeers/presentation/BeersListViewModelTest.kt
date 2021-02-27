package com.simtop.billionbeers.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.simtop.billionbeers.*
import com.simtop.beerdomain.core.Either
import com.simtop.beerdomain.domain.usecases.GetAllBeersUseCase
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

    private val getAllBeersUseCase: com.simtop.beerdomain.domain.usecases.GetAllBeersUseCase = mockk()

    @Test
    fun `when usecase succeeds we get success state`() = coroutineScope.runBlocking {
        // Arrange

        coEvery {
            getAllBeersUseCase.execute(any())
        } returns com.simtop.beerdomain.core.Either.Right(fakeBeerListModel)

        // Act

        coroutineScope.dispatcher.pauseDispatcher()

        val beersListViewModel = BeersListViewModel(coroutineScope.testDispatcherProvider, getAllBeersUseCase)

        val liveDataUnderTest = beersListViewModel.beerListViewState.testObserver()

        coroutineScope.dispatcher.resumeDispatcher()

        // Assert

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
    fun `when usecase fails we get error state`() = coroutineScope.runBlocking {
        // Arrange

        coEvery {
            getAllBeersUseCase.execute(any())
        } returns com.simtop.beerdomain.core.Either.Left(fakeException)

        // Act

        coroutineScope.dispatcher.pauseDispatcher()

        val beersListViewModel = BeersListViewModel(coroutineScope.testDispatcherProvider, getAllBeersUseCase)

        val liveDataUnderTest = beersListViewModel.beerListViewState.testObserver()

        coroutineScope.dispatcher.resumeDispatcher()

        // Assert

        coVerify(exactly = 1) {
            getAllBeersUseCase.execute(any())
        }

        liveDataUnderTest.observedValues.size shouldBeEqualTo 2
        liveDataUnderTest.observedValues[0] shouldBeEqualTo BeersListViewState.Loading
        liveDataUnderTest.observedValues[1] shouldBeEqualTo BeersListViewState.Error(fakeErrorName)
    }
}