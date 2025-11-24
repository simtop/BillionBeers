package com.simtop.billionbeers.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.simtop.beerdomain.domain.usecases.GetAllBeersUseCase
import com.simtop.beerdomain.domain.usecases.LoadNextPageUseCase
import com.simtop.beerdomain.domain.usecases.ObservePagingStateUseCase
import com.simtop.billionbeers.testing_utils.*
import com.simtop.core.core.PagingState
import com.simtop.feature.beerslist.BeersListViewModel
import com.simtop.feature.beerslist.BeersListViewState
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
    private val observePagingStateUseCase: ObservePagingStateUseCase = mockk()
    private val loadNextPageUseCase: LoadNextPageUseCase = mockk()

    @Test
    fun `when usecase succeeds we get success state`() = coroutineScope.runBlocking {
        // Arrange

        val captureSlot = slot<GetAllBeersUseCase.Params>()

        coEvery {
            getAllBeersUseCase.execute(capture(captureSlot))
        } returns flowOf(fakeBeerListModel)

        coEvery {
            observePagingStateUseCase.execute()
        } returns flowOf(PagingState.Idle)

        // Act

        val beersListViewModel =
            BeersListViewModel(
                coroutineScope.testDispatcherProvider,
                getAllBeersUseCase,
                observePagingStateUseCase,
                loadNextPageUseCase
            )

        // Assert

        expect {
            that(captureSlot) {
                get { captured.quantity }.isEqualTo(1)
            }
            // Initial state is Loading
            that(beersListViewModel.beerListViewState.value).isEqualTo(BeersListViewState.Success(fakeBeerListModel))
        }
    }
}