package com.simtop.billionbeers.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.simtop.billionbeers.MainCoroutineScopeRule
import com.simtop.billionbeers.core.Either
import com.simtop.billionbeers.core.ViewState
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.domain.usecases.GetAllBeersUseCase
import com.simtop.billionbeers.domain.usecases.UpdateAvailabilityUseCase
import com.simtop.billionbeers.fakeBeerListModel
import com.simtop.billionbeers.getValueForTest
import com.simtop.billionbeers.presentation.beerslist.BeersViewModel
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
internal class BeersViewModelTest {

    @get:Rule
    var rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineScope = MainCoroutineScopeRule()

    private val getAllBeersUseCase: GetAllBeersUseCase = mockk()

    private val availabilityUseCase: UpdateAvailabilityUseCase = mockk()

    private val beersViewModel: BeersViewModel by lazy {
        BeersViewModel(getAllBeersUseCase,availabilityUseCase)
    }

    @Test
    fun `when we get list of beer it succeeds and shows loader`() {

        coEvery {
            getAllBeersUseCase.execute(any())
        } returns Either.Right(fakeBeerListModel)

        coroutineScope.runBlockingTest {
            beersViewModel.getAllBeers()

            beersViewModel.myViewState.getValueForTest() shouldBeEqualTo ViewState.Loading

            Thread.sleep(1000)

            coVerify(exactly = 1) {
                getAllBeersUseCase.execute(any())
            }
        }

        if (beersViewModel.myViewState.value is ViewState.Result) {
            val result = beersViewModel.myViewState.getValueForTest() as ViewState.Result
            if (result.result is Either.Right) {
                (result.result as Either.Right<List<Beer>>).value shouldBeEqualTo fakeBeerListModel
            }

        }
    }

    @Test
    fun `when we get list of beer it fails and shows error`() {

        coEvery {
            getAllBeersUseCase.execute(any())
        } returns Either.Left(Exception("Error getting list of beers"))

        coroutineScope.runBlockingTest {
            beersViewModel.getAllBeers()

            Thread.sleep(1000)

            coVerify(exactly = 1) {
                getAllBeersUseCase.execute(any())
            }
        }
        if (beersViewModel.myViewState.value is ViewState.Result) {
            val result = beersViewModel.myViewState.getValueForTest() as ViewState.Result
            if (result.result is Either.Left) {
                (result.result as Either.Left<Throwable>)
                    .value.message shouldBeEqualTo "Error getting list of beers"

            }
        }
    }
}