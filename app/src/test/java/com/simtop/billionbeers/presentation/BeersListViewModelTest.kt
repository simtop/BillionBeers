package com.simtop.billionbeers.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.simtop.billionbeers.MainCoroutineScopeRule
import com.simtop.billionbeers.core.Either
import com.simtop.billionbeers.domain.usecases.GetAllBeersUseCase
import com.simtop.billionbeers.fakeBeerListModel
import com.simtop.billionbeers.getValueForTest
import com.simtop.billionbeers.presentation.beerslist.BeersListViewModel
import com.simtop.billionbeers.presentation.beerslist.BeersListViewState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
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

    private val beersListViewModel: BeersListViewModel by lazy {
        BeersListViewModel(getAllBeersUseCase)
    }

    @Test
    fun `when we get list of beer it succeeds and shows loader`() {

        coEvery {
            getAllBeersUseCase.execute(any())
        } returns flow { Either.Right(fakeBeerListModel) }

        coroutineScope.runBlockingTest {
            beersListViewModel.getAllBeers()

            beersListViewModel.beerListViewState.value shouldBeEqualTo BeersListViewState.Loading

            Thread.sleep(1000)

            coVerify(exactly = 1) {
                getAllBeersUseCase.execute(any())
            }
        }
        val response = beersListViewModel.beerListViewState.value

        if (response is BeersListViewState.Success) {
            response.result shouldBeEqualTo fakeBeerListModel
        }
    }

    @Test
    fun `when we get list of beer it fails and shows error`() {

        coEvery {
            getAllBeersUseCase.execute(any())
        } returns flow { Either.Left(Exception("Error getting list of beers")) }

        coroutineScope.runBlockingTest {
            beersListViewModel.getAllBeers()

            Thread.sleep(1000)

            coVerify(exactly = 1) {
                getAllBeersUseCase.execute(any())
            }
        }

        val response = beersListViewModel.beerListViewState.value

        if (response is BeersListViewState.Error) {
            response.result.message shouldBeEqualTo "Error getting list of beers"
        }
    }
}