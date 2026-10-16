package com.simtop.feature.favorites

import app.cash.turbine.test
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.billionbeers.testing_utils.MainDispatcherExtension
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.FLOW_RETRY_DELAY_MILLIS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

@OptIn(ExperimentalCoroutinesApi::class)
internal class FavoritesViewModelTest {

  @JvmField @RegisterExtension val mainDispatcher = MainDispatcherExtension()
  private val dispatcher
    get() = mainDispatcher.testDispatcher

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
  fun `upstream failure emits error state and recovers on retry`() =
    runTest(dispatcher) {
      var subscriptions = 0
      val favorite = Beer.empty.copy(id = "1", name = "Favorite", isFavorite = true)
      val repository =
        FakeBeersRepository().apply {
          setFavoriteBeersObservationFlow(
            flow {
              if (subscriptions++ == 0) error("database unavailable")
              emit(listOf(favorite))
              awaitCancellation()
            }
          )
        }
      val viewModel = FavoritesViewModel(repository)

      viewModel.viewState.test {
        expectThat(awaitItem()).isA<CommonUiState.Loading>()
        val error = awaitItem()
        expectThat(error).isA<CommonUiState.Error>()
        advanceTimeBy(FLOW_RETRY_DELAY_MILLIS)
        dispatcher.scheduler.runCurrent()
        val recovered = awaitItem()
        expectThat(recovered).isA<CommonUiState.Success<List<Beer>>>()
        expectThat((recovered as CommonUiState.Success).data).isEqualTo(listOf(favorite))
        expectThat(subscriptions).isEqualTo(2)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `while subscribed restarts the source after its stop timeout`() =
    runTest(dispatcher) {
      var subscriptions = 0
      val repository =
        FakeBeersRepository().apply {
          setFavoriteBeersObservationFlow(
            flow {
              subscriptions++
              emit(emptyList())
              kotlinx.coroutines.awaitCancellation()
            }
          )
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
