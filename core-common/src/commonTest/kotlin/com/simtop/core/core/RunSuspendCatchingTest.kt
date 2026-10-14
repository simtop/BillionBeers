package com.simtop.core.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

class RunSuspendCatchingTest {
  @Test
  fun returnsSuccessfulValue() = runTest {
    assertEquals(42, runSuspendCatching { 42 }.getOrThrow())
  }

  @Test
  fun wrapsNonCancellationFailure() = runTest {
    val failure = IllegalStateException("image decode failed")

    val result = runSuspendCatching<Int> { throw failure }

    assertSame(failure, result.exceptionOrNull())
  }

  @Test
  fun rethrowsCancellation() = runTest {
    val cancellation = CancellationException("image load cancelled")

    val result = runCatching { runSuspendCatching<Int> { throw cancellation } }

    assertSame(cancellation, result.exceptionOrNull())
  }
}
