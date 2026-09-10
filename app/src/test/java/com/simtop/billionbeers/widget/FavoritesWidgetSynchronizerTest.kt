package com.simtop.billionbeers.widget

import com.simtop.beerdomain.fakes.FakeBeersRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
