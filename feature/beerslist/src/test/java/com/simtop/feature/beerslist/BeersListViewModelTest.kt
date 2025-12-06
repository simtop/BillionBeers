package com.simtop.feature.beerslist

import app.cash.turbine.test
import com.simtop.beerdomain.domain.GetAllBeersUseCase
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.usecases.LoadNextPageUseCase
import com.simtop.beerdomain.domain.usecases.ObservePagingStateUseCase
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.PagingState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class BeersListViewModelTest {

  private val coroutineDispatcherProvider = mockk<CoroutineDispatcherProvider>()
  private val fakeBeersRepository = FakeBeersRepository()

  // Real UseCases
  private val getAllBeersUseCase = GetAllBeersUseCase(fakeBeersRepository)
  private val observePagingStateUseCase = ObservePagingStateUseCase(fakeBeersRepository)
  private val loadNextPageUseCase = LoadNextPageUseCase(fakeBeersRepository)

  private lateinit var testDispatcher: TestDispatcher
  private lateinit var viewModel: BeersListViewModel

  @BeforeEach
  fun setUp() {
    testDispatcher = UnconfinedTestDispatcher()
    Dispatchers.setMain(testDispatcher)
    every { coroutineDispatcherProvider.io } returns testDispatcher
    every { coroutineDispatcherProvider.main } returns testDispatcher

    viewModel =
      BeersListViewModel(
        coroutineDispatcherProvider,
        getAllBeersUseCase,
        observePagingStateUseCase,
        loadNextPageUseCase
      )
  }

  @AfterEach
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `when onScrollToBottom is called, isLoadingNextPage should be true`() =
    runTest(testDispatcher) {
      val beer = Beer.empty.copy(id = "1", name = "Test Beer")

      viewModel.beerListViewState.test {
        // Initial state (Loading)
        assert(awaitItem() is CommonUiState.Loading)

        // Trigger data load
        fakeBeersRepository.setBeers(listOf(beer))

        // Success state from repository flow
        val successState = awaitItem() as CommonUiState.Success
        assert(successState.data.beers.size == 1)
        assert(!successState.data.isLoadingNextPage)

        // Trigger scroll to bottom
        viewModel.onScrollToBottom()

        // Simulate PagingState update via Fake Repository
        fakeBeersRepository.setPagingState(PagingState.LoadingNextPage)

        // Expect update with isLoadingNextPage = true
        val loadingNextPageState = awaitItem() as CommonUiState.Success
        assert(loadingNextPageState.data.isLoadingNextPage)

        // Simulate PagingState Success
        fakeBeersRepository.setPagingState(PagingState.Success)

        // Expect update with isLoadingNextPage = false
        val finalState = awaitItem() as CommonUiState.Success
        assert(!finalState.data.isLoadingNextPage)
      }
    }
}
