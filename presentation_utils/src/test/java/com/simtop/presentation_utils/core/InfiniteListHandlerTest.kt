package com.simtop.presentation_utils.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class InfiniteListHandlerTest {

  private suspend fun signalsFor(vararg positions: ListPosition, buffer: Int = 1): Int =
    flowOf(*positions).loadMoreSignals(buffer).toList().size

  @Test
  fun `reaching the bottom fires exactly one signal`() = runTest {
    val count =
      signalsFor(
        ListPosition(totalItems = 25, lastVisibleIndex = 5), // mid-list: not near bottom
        ListPosition(totalItems = 25, lastVisibleIndex = 24), // last item visible: near bottom
      )

    count shouldBeEqualTo 1
  }

  // The key regression guard: the same at-bottom count arriving twice (a load in flight, or a load
  // that just failed - neither grows the list) must NOT re-fire. This is what the stale-flag
  // implementation got wrong.
  @Test
  fun `the same at-bottom count does not re-fire`() = runTest {
    val count =
      signalsFor(
        ListPosition(totalItems = 25, lastVisibleIndex = 24),
        ListPosition(totalItems = 25, lastVisibleIndex = 24),
      )

    count shouldBeEqualTo 1
  }

  @Test
  fun `a longer list re-arms the next signal`() = runTest {
    val count =
      signalsFor(
        ListPosition(totalItems = 25, lastVisibleIndex = 24), // bottom of page 1 -> fire
        ListPosition(totalItems = 50, lastVisibleIndex = 24), // page 2 loaded, no longer at bottom
        ListPosition(totalItems = 50, lastVisibleIndex = 49), // bottom of page 2 -> fire again
      )

    count shouldBeEqualTo 2
  }

  @Test
  fun `staying away from the bottom never fires`() = runTest {
    val count =
      signalsFor(
        ListPosition(totalItems = 50, lastVisibleIndex = 5),
        ListPosition(totalItems = 50, lastVisibleIndex = 10),
      )

    count shouldBeEqualTo 0
  }

  @Test
  fun `buffer widens the near-bottom trigger`() = runTest {
    // With buffer 5, item 20 of 25 already counts as "near bottom" (20 + 1 > 25 - 5).
    val count = signalsFor(ListPosition(totalItems = 25, lastVisibleIndex = 20), buffer = 5)

    count shouldBeEqualTo 1
  }
}
