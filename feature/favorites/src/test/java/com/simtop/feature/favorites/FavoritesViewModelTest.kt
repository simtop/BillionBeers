package com.simtop.feature.favorites

import app.cash.turbine.test
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.core.core.CommonUiState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

@OptIn(ExperimentalCoroutinesApi::class)
internal class FavoritesViewModelTest {

  private val dispatcher = StandardTestDispatcher()

  @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)

  @AfterEach fun tearDown() = Dispatchers.resetMain()

  @Test
  fun `view state starts loading and maps empty and favorite emissions`() =
    runTest(dispatcher) {
      val repository = FakeBeersRepository()
      val viewModel = FavoritesViewModel(repository)

      expectThat(viewModel.viewState.value).isA<CommonUiState.Loading>()
      viewModel.viewState.test {
        expectThat(awaitItem()).isA<CommonUiState.Loading>()
        dispatcher.scheduler.runCurrent()

        repository.setBeers(emptyList())
        expectThat(awaitItem()).isA<CommonUiState.Empty>()

        val beer = Beer.empty.copy(id = "1", name = "Favorite", isFavorite = true)
        repository.setBeers(listOf(beer))
        val success = awaitItem()
        expectThat(success).isA<CommonUiState.Success<List<Beer>>>()
        expectThat((success as CommonUiState.Success).data).isEqualTo(listOf(beer))
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `upstream failure maps to error state`() =
    runTest(dispatcher) {
      val repository =
        mockk<BeersRepository> {
          every { observeFavoriteBeers() } returns
            flow {
              throw IllegalStateException("favorites unavailable")
            }
        }
      val viewModel = FavoritesViewModel(repository)

      viewModel.viewState.test {
        expectThat(awaitItem()).isA<CommonUiState.Loading>()
        val error = awaitItem()
        expectThat(error).isA<CommonUiState.Error>()
        expectThat((error as CommonUiState.Error).message).isEqualTo("favorites unavailable")
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `while subscribed restarts the source after its stop timeout`() =
    runTest(dispatcher) {
      var subscriptions = 0
      val repository =
        mockk<BeersRepository> {
          every { observeFavoriteBeers() } returns
            flow {
              subscriptions++
              emit(emptyList())
              kotlinx.coroutines.awaitCancellation()
            }
        }
      val viewModel = FavoritesViewModel(repository)

      viewModel.viewState.test {
        expectThat(awaitItem()).isA<CommonUiState.Loading>()
        expectThat(awaitItem()).isA<CommonUiState.Empty>()
        dispatcher.scheduler.runCurrent()
        cancelAndIgnoreRemainingEvents()
      }
      advanceTimeBy(5_001)
      dispatcher.scheduler.runCurrent()
      viewModel.viewState.test {
        expectThat(awaitItem()).isA<CommonUiState.Empty>()
        dispatcher.scheduler.runCurrent()
        expectThat(subscriptions).isEqualTo(2)
        cancelAndIgnoreRemainingEvents()
      }
    }
}
