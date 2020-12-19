package com.simtop.billionbeers.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.simtop.billionbeers.MainCoroutineScopeRule
import com.simtop.billionbeers.core.Either
import com.simtop.billionbeers.domain.usecases.GetAllBeersUseCase
import com.simtop.billionbeers.fakeBeerListModel
import com.simtop.billionbeers.presentation.beerslist.BeersListViewModel
import com.simtop.billionbeers.presentation.beerslist.BeersListViewState
import com.simtop.billionbeers.testObserver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
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
    fun `when we get list of beer it succeeds and shows loader`() = coroutineScope.runBlockingTest {
        coEvery {
            getAllBeersUseCase.execute(any())
        } returns Either.Right(fakeBeerListModel)

        val beersListViewModel = BeersListViewModel(getAllBeersUseCase)

        val liveDataUnderTest = beersListViewModel.beerListViewState.testObserver()

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
    fun `when we get list of beer it fails and shows error`() = coroutineScope.runBlockingTest {
        coEvery {
            getAllBeersUseCase.execute(any())
        } returns Either.Left(Exception("Error getting list of beers"))
        val beersListViewModel = BeersListViewModel(getAllBeersUseCase)

        val liveDataUnderTest = beersListViewModel.beerListViewState.testObserver()

        coVerify(exactly = 1) {
            getAllBeersUseCase.execute(any())
        }

        println(liveDataUnderTest.observedValues.toString())
        liveDataUnderTest.observedValues.size shouldBeEqualTo 2
        liveDataUnderTest.observedValues[0] shouldBeEqualTo BeersListViewState.Loading
        liveDataUnderTest.observedValues[1] shouldBeEqualTo BeersListViewState.Error("Error getting list of beers")
    }
}