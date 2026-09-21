package com.simtop.billionbeers.shared.favorites

import app.cash.turbine.test
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.core.core.CommonUiState
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)

  @AfterEach fun tearDown() = Dispatchers.resetMain()

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
  fun `upstream failure maps to error state`() =
    runTest(dispatcher) {
      val repository =
        FakeBeersRepository().apply {
          setFavoriteBeersObservationFlow(
            flow { throw IllegalStateException("favorites unavailable") }
          )
        }
      val viewModel = FavoritesViewModel(repository)

      viewModel.viewState.test {
        awaitItem()
        assertEquals("favorites unavailable", assertIs<CommonUiState.Error>(awaitItem()).message)
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
