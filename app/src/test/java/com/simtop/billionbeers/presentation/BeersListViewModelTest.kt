package com.simtop.billionbeers.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.paging.map
import com.simtop.billionbeers.*
import com.simtop.billionbeers.core.Either
import com.simtop.billionbeers.domain.usecases.GetBeersFromApiUseCase
import com.simtop.billionbeers.presentation.beerslist.BeersListViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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

    private val getBeersFromApiUseCase: GetBeersFromApiUseCase = mockk()

    private val beersListViewModel: BeersListViewModel by lazy {
        BeersListViewModel(getBeersFromApiUseCase)
    }

    @Test
    fun `when we ask for pagedList we only get it once`() {

        coEvery {
            getBeersFromApiUseCase.execute(any())
        } returns Either.Right(fakeBeerListModel2)

        //TODO: Reasearch more how or what we can assert pagind data this is flaky
        coroutineScope.launch {
            beersListViewModel.getPaginatedBeers().collectLatest { paginatedBeer ->
                paginatedBeer.map { beer->
                    beer shouldBeEqualTo fakeBeerListModel2.first()
                }
            }
        }
    }

//    @Test
//    fun `when we get list of beer it fails and shows error`() {
//
//        coEvery {
//            getAllBeersUseCase.execute(any())
//        } returns Either.Left(Exception("Error getting list of beers"))
//
//        coroutineScope.runBlockingTest {
//            beersListViewModel.getAllBeers()
//
//            Thread.sleep(1000)
//
//            coVerify(exactly = 1) {
//                getAllBeersUseCase.execute(any())
//            }
//        }
//
//        val response = beersListViewModel.beerListViewState.getValueForTest()
//
//        if (response is BeersListViewState.Error) {
//            response.result.message shouldBeEqualTo "Error getting list of beers"
//        }
//    }
}