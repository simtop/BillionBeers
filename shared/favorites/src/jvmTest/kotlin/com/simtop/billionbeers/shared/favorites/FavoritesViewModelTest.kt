package com.simtop.billionbeers.shared.favorites

import app.cash.turbine.test
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.billionbeers.testing_utils.MainDispatcherExtension
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.FLOW_RETRY_DELAY_MILLIS
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {
  @JvmField @RegisterExtension val mainDispatcher = MainDispatcherExtension()
  private val dispatcher
    get() = mainDispatcher.testDispatcher

  @Test
  fun `view state starts loading and maps empty and favorite emissions`() =
    runTest(dispatcher) {
      val repository = FakeBeersRepository()
      val viewModel = FavoritesViewModel(repository)

      assertIs<CommonUiState.Loading>(viewModel.viewState.value)
      viewModel.viewState.test {
        awaitItem()
        dispatcher.scheduler.runCurrent()
        repository.setBeers(emptyList())
        assertIs<CommonUiState.Empty>(awaitItem())
        val beer = Beer.empty.copy(id = "1", name = "Favorite", isFavorite = true)
        repository.setBeers(listOf(beer))
        assertEquals(listOf(beer), assertIs<CommonUiState.Success<List<Beer>>>(awaitItem()).data)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `upstream failure emits error state and recovers on retry`() =
    runTest(dispatcher) {
      var subscriptions = 0
      val favorites = listOf(Beer.empty.copy(id = "1", name = "Favorite", isFavorite = true))
      val repository =
        FakeBeersRepository().apply {
          setFavoriteBeersObservationFlow(
            flow {
              if (subscriptions++ == 0) error("database unavailable")
              emit(favorites)
              awaitCancellation()
            }
          )
        }
      val viewModel = FavoritesViewModel(repository)

      viewModel.viewState.test {
        awaitItem()
        assertIs<CommonUiState.Error>(awaitItem())
        advanceTimeBy(FLOW_RETRY_DELAY_MILLIS)
        dispatcher.scheduler.runCurrent()
        assertEquals(favorites, assertIs<CommonUiState.Success<List<Beer>>>(awaitItem()).data)
        assertEquals(2, subscriptions)
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
              awaitCancellation()
            }
          )
        }
      val viewModel = FavoritesViewModel(repository)

      viewModel.viewState.test {
        awaitItem()
        assertIs<CommonUiState.Empty>(awaitItem())
        dispatcher.scheduler.runCurrent()
        cancelAndIgnoreRemainingEvents()
      }
      advanceTimeBy(5_001)
      dispatcher.scheduler.runCurrent()
      viewModel.viewState.test {
        assertIs<CommonUiState.Empty>(awaitItem())
        dispatcher.scheduler.runCurrent()
        assertEquals(2, subscriptions)
        cancelAndIgnoreRemainingEvents()
      }
    }
}
