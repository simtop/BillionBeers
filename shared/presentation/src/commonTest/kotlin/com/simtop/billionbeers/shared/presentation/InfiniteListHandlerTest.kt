package com.simtop.billionbeers.shared.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class InfiniteListHandlerTest {
  private suspend fun signalsFor(vararg positions: ListPosition, buffer: Int = 1): Int =
    flowOf(*positions).loadMoreSignals(buffer).toList().size

  @Test
  fun reachingTheBottomFiresOnce() = runTest {
    assertEquals(
      1,
      signalsFor(
        ListPosition(totalItems = 25, lastVisibleIndex = 5),
        ListPosition(totalItems = 25, lastVisibleIndex = 24),
      ),
    )
  }

  @Test
  fun sameAtBottomCountDoesNotRefire() = runTest {
    assertEquals(
      1,
      signalsFor(
        ListPosition(totalItems = 25, lastVisibleIndex = 24),
        ListPosition(totalItems = 25, lastVisibleIndex = 24),
      ),
    )
  }

  @Test
  fun listGrowthRearmsTheNextSignal() = runTest {
    assertEquals(
      2,
      signalsFor(
        ListPosition(totalItems = 25, lastVisibleIndex = 24),
        ListPosition(totalItems = 50, lastVisibleIndex = 24),
        ListPosition(totalItems = 50, lastVisibleIndex = 49),
      ),
    )
  }

  @Test
  fun emptyPreLayoutDoesNotFireOrConsumeTheFirstRealSignal() = runTest {
    assertEquals(
      1,
      signalsFor(
        ListPosition(totalItems = 0, lastVisibleIndex = 0),
        ListPosition(totalItems = 25, lastVisibleIndex = 24),
      ),
    )
  }

  @Test
  fun awayFromTheBottomDoesNotFire() = runTest {
    assertEquals(
      0,
      signalsFor(
        ListPosition(totalItems = 50, lastVisibleIndex = 5),
        ListPosition(totalItems = 50, lastVisibleIndex = 10),
      ),
    )
  }

  @Test
  fun bufferWidensTheNearBottomTrigger() = runTest {
    assertEquals(
      1,
      signalsFor(ListPosition(totalItems = 25, lastVisibleIndex = 20), buffer = 5),
    )
  }
}
