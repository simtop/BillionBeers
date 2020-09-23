package com.simtop.billionbeers.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingData
import com.simtop.billionbeers.MainCoroutineScopeRule
import com.simtop.billionbeers.domain.repository.BeersRepository
import com.simtop.billionbeers.fakeBeerListModel
import com.simtop.billionbeers.fakeFlowPagingBeer
import com.simtop.billionbeers.presentation.beerslist.BeersListViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@ExperimentalPagingApi
@ExperimentalCoroutinesApi
internal class BeersListViewModelTest {

    @get:Rule
    var rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineScope = MainCoroutineScopeRule()

    private val repository: BeersRepository = mockk()

    private val beersListViewModel: BeersListViewModel by lazy {
        BeersListViewModel(repository)
    }

    //TODO: I need to check how to test more the view model with paging
    @Test
    fun `when we ask for pagedList we only get it once`() {

        coEvery {
            repository.getPaginatedBeers()
        } returns fakeFlowPagingBeer

        beersListViewModel.getPagedBeerListFlow()

        coVerify(exactly = 1) {
            repository.getPaginatedBeers()
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
//        if(response is BeersListViewState.Error)  {
//            response.result.message shouldBeEqualTo "Error getting list of beers"
//        }
//    }
}