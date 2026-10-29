package com.simtop.billionbeers.widget

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.core.core.FLOW_RETRY_DELAY_MILLIS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesWidgetSynchronizerTest {
  @Test
  fun updatesOnlyWhenVisibleWidgetItemsChange() = runTest {
    val repository = FakeBeersRepository()
    var updateCount = 0
    val synchronizer =
      FavoritesWidgetSynchronizer(
        repository = repository,
        updateWidget = { updateCount++ },
        onUpdateFailure = { throw AssertionError(it) },
      )

    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { synchronizer.run() }
    runCurrent()
    assertEquals(1, updateCount)

    repository.setBeers(listOf(beer(id = "2", name = "Bravo"), beer(id = "1", name = "Alpha")))
    runCurrent()
    assertEquals(2, updateCount)

    repository.setBeers(
      listOf(
        beer(id = "2", name = "Bravo"),
        beer(id = "1", name = "Alpha", availability = false),
      )
    )
    runCurrent()
    assertEquals(3, updateCount)

    repository.setBeers(
      listOf(
        beer(id = "2", name = "Bravo"),
        beer(id = "1", name = "Alpha", availability = false),
        beer(id = "4", name = "Delta"),
        beer(id = "3", name = "Charlie"),
      )
    )
    runCurrent()
    assertEquals(4, updateCount)

    repository.setBeers(
      listOf(
        beer(id = "2", name = "Bravo"),
        beer(id = "1", name = "Alpha", availability = false),
        beer(id = "4", name = "Delta"),
        beer(id = "3", name = "Charlie"),
        beer(id = "5", name = "Echo"),
      )
    )
    runCurrent()
    assertEquals(4, updateCount)

    repository.setBeers(emptyList())
    runCurrent()
    assertEquals(5, updateCount)
  }

  @Test
  fun continuesAfterOneWidgetUpdateFails() = runTest {
    val repository = FakeBeersRepository()
    val failures = mutableListOf<Throwable>()
    var attempts = 0
    val synchronizer =
      FavoritesWidgetSynchronizer(
        repository = repository,
        updateWidget = {
          attempts++
          if (attempts == 1) error("first update failed")
        },
        onUpdateFailure = failures::add,
      )

    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { synchronizer.run() }
    runCurrent()
    repository.setBeers(listOf(beer("1")))
    runCurrent()

    assertEquals(2, attempts)
    assertEquals(listOf("first update failed"), failures.map(Throwable::message))
  }

  @Test
  fun retriesInitialDatabaseReadFailureAndContinuesUpdating() = runTest {
    val repository = FakeBeersRepository()
    val favorites = MutableStateFlow(listOf(beer("1")))
    var subscriptions = 0
    repository.setFavoriteBeersObservationFlow(
      flow {
        if (subscriptions++ == 0) error("temporary database read failure")
        emitAll(favorites)
      }
    )
    val failures = mutableListOf<Throwable>()
    var updates = 0
    val synchronizer = FavoritesWidgetSynchronizer(repository, { updates++ }, failures::add)
    val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { synchronizer.run() }

    runCurrent()
    assertTrue(job.isActive)
    assertEquals(1, updates)
    assertEquals(listOf("temporary database read failure"), failures.map(Throwable::message))
    advanceTimeBy(FLOW_RETRY_DELAY_MILLIS)
    runCurrent()
    assertEquals(2, subscriptions)
    assertEquals(2, updates)

    favorites.value = listOf(beer("2"))
    runCurrent()
    assertEquals(3, updates)
    job.cancel()
    advanceTimeBy(FLOW_RETRY_DELAY_MILLIS * 2)
    runCurrent()
    assertEquals(2, subscriptions)
  }

  @Test
  fun keepsLastGoodProjectionDuringReadFailureAndThenRecovers() = runTest {
    val repository = FakeBeersRepository()
    var subscriptions = 0
    repository.setFavoriteBeersObservationFlow(
      flow {
        if (subscriptions++ == 0) {
          emit(listOf(beer("1")))
          error("read failed after a good snapshot")
        }
        emit(listOf(beer("2")))
        awaitCancellation()
      }
    )
    val emissions = mutableListOf<List<FavoritesWidgetItem>>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeFavoritesWidgetItems().collect { emissions += it }
    }

    runCurrent()
    assertEquals(listOf(listOf(beer("1")).toFavoritesWidgetItems()), emissions)
    advanceTimeBy(FLOW_RETRY_DELAY_MILLIS)
    runCurrent()
    assertEquals(
      listOf(
        listOf(beer("1")).toFavoritesWidgetItems(),
        listOf(beer("2")).toFavoritesWidgetItems(),
      ),
      emissions,
    )
    assertEquals(2, subscriptions)
  }

  @Test
  fun observationCancellationDoesNotReportOrRetryAnError() = runTest {
    val repository = FakeBeersRepository()
    var subscriptions = 0
    repository.setFavoriteBeersObservationFlow(
      flow<List<Beer>> {
        subscriptions++
        throw CancellationException("observation cancelled")
      }
    )
    val failures = mutableListOf<Throwable>()
    val job =
      backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        repository.observeFavoritesWidgetItems(failures::add).collect {}
      }

    runCurrent()
    assertTrue(job.isCancelled)
    advanceTimeBy(FLOW_RETRY_DELAY_MILLIS * 2)
    runCurrent()
    assertEquals(1, subscriptions)
    assertTrue(failures.isEmpty())
  }
}
