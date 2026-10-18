package com.simtop.core.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class FlowRetryTest {
  @Test
  fun `explicit cancellation escapes without emitting an error state`() = runTest {
    val cancellation = CancellationException("cancel observation")
    val emitted = mutableListOf<Int>()

    val thrown =
      assertFailsWith<CancellationException> {
        flow<Int> { throw cancellation }
          .retryWithErrorState {
            emitted += -1
            -1
          }
          .toList()
      }

    assertSame(cancellation, thrown)
    assertEquals(emptyList(), emitted)
  }
}
