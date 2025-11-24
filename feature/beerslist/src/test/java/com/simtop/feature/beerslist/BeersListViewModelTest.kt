package com.simtop.feature.beerslist

import app.cash.turbine.test
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.usecases.GetAllBeersUseCase
import com.simtop.beerdomain.domain.usecases.LoadNextPageUseCase
import com.simtop.beerdomain.domain.usecases.ObservePagingStateUseCase
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.PagingState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class BeersListViewModelTest {

    private val coroutineDispatcherProvider = mockk<CoroutineDispatcherProvider>()
    private val getAllBeersUseCase = mockk<GetAllBeersUseCase>()
    private val observePagingStateUseCase = mockk<ObservePagingStateUseCase>()
    private val loadNextPageUseCase = mockk<LoadNextPageUseCase>()

    private lateinit var testDispatcher: TestDispatcher

    private lateinit var viewModel: BeersListViewModel

    private val beersFlow = MutableStateFlow<List<Beer>>(emptyList())
    private val pagingStateFlow = MutableStateFlow<PagingState>(PagingState.Idle)

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        every { coroutineDispatcherProvider.io } returns testDispatcher
        every { coroutineDispatcherProvider.main } returns testDispatcher
        
        every { getAllBeersUseCase.execute(any()) } returns beersFlow
        every { observePagingStateUseCase.execute() } returns pagingStateFlow
        coEvery { loadNextPageUseCase.execute() } returns Unit

        viewModel = BeersListViewModel(
            coroutineDispatcherProvider,
            getAllBeersUseCase,
            observePagingStateUseCase,
            loadNextPageUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when onScrollToBottom is called, isLoadingNextPage should be true`() = runTest(testDispatcher) {
        val beer = mockk<Beer>(relaxed = true)
        beersFlow.value = listOf(beer)
        
        viewModel.beerListViewState.test {
            // Initial state (Loading)
            assert(awaitItem() is BeersListViewState.Loading)
            
            // Success state from beersFlow
            val successState = awaitItem() as BeersListViewState.Success
            assert(successState.result.size == 1)
            assert(!successState.isLoadingNextPage)

            // Trigger scroll to bottom
            viewModel.onScrollToBottom()
            
            // Simulate PagingState update
            pagingStateFlow.value = PagingState.LoadingNextPage
            
            // Expect update with isLoadingNextPage = true
            val loadingNextPageState = awaitItem() as BeersListViewState.Success
            assert(loadingNextPageState.isLoadingNextPage)
            
            // Simulate PagingState Success
            pagingStateFlow.value = PagingState.Success
            
            // Expect update with isLoadingNextPage = false
            val finalState = awaitItem() as BeersListViewState.Success
            assert(!finalState.isLoadingNextPage)
        }
    }
}
