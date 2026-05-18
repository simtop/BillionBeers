package com.simtop.feature.beerslist

import app.cash.turbine.test
import com.simtop.beerdomain.domain.GetAllBeersUseCase
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.usecases.LoadNextPageUseCase
import com.simtop.beerdomain.domain.usecases.ObservePagingStateUseCase
import com.simtop.beerdomain.domain.usecases.RefreshBeersUseCase
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
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

@ExperimentalCoroutinesApi
class BeersListViewModelTest {

  private val coroutineDispatcherProvider = mockk<CoroutineDispatcherProvider>()
  private val fakeBeersRepository = FakeBeersRepository()

  // Real UseCases
  private val getAllBeersUseCase = GetAllBeersUseCase(fakeBeersRepository)
  private val observePagingStateUseCase = ObservePagingStateUseCase(fakeBeersRepository)
  private val loadNextPageUseCase = LoadNextPageUseCase(fakeBeersRepository)
  private val refreshBeersUseCase = RefreshBeersUseCase(fakeBeersRepository)

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
        loadNextPageUseCase,
        refreshBeersUseCase,
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
        expectThat(awaitItem()).isA<CommonUiState.Loading>()

        // Trigger data load
        fakeBeersRepository.setBeers(listOf(beer))

        // Success state from repository flow
        val successState = awaitItem()
        expectThat(successState).isA<CommonUiState.Success<BeersListUiModel>>()
        expectThat((successState as CommonUiState.Success).data.beers.size).isEqualTo(1)
        expectThat(successState.data.isLoadingNextPage).isFalse()

        // Trigger scroll to bottom
        viewModel.onScrollToBottom()

        // Simulate PagingState update via Fake Repository
        fakeBeersRepository.setPagingState(PagingState.LoadingNextPage)

        // Expect update with isLoadingNextPage = true
        val loadingNextPageState = awaitItem()
        expectThat(loadingNextPageState).isA<CommonUiState.Success<BeersListUiModel>>()
        expectThat((loadingNextPageState as CommonUiState.Success).data.isLoadingNextPage).isTrue()

        // Simulate PagingState Success
        fakeBeersRepository.setPagingState(PagingState.Success)

        // Expect update with isLoadingNextPage = false
        val finalState = awaitItem()
        expectThat(finalState).isA<CommonUiState.Success<BeersListUiModel>>()
        expectThat((finalState as CommonUiState.Success).data.isLoadingNextPage).isFalse()
      }
    }
}
