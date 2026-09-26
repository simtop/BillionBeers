package com.simtop.billionbeers.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class WebStartupCoordinatorTest {
  @Test
  fun retryUsesAFreshRuntimeAfterOpenFailure() = runTest {
    var attempts = 0
    val runtimes = mutableListOf<FakeRuntime>()
    val coordinator =
      WebStartupCoordinator<FakeRuntime, FakeSession>(
        openRuntime = {
          attempts += 1
          if (attempts == 1) error("open failed")
          FakeRuntime(attempts).also(runtimes::add)
        },
        createSession = { runtime -> FakeSession(runtime.id) },
        closeRuntime = {},
      )

    val failed = coordinator.start()
    val ready = coordinator.start()

    assertEquals("open failed", assertIs<WebStartupResult.Failed>(failed).message)
    val successful = assertIs<WebStartupResult.Ready<FakeRuntime, FakeSession>>(ready)
    assertEquals(2, attempts)
    assertEquals(FakeRuntime(2), successful.runtime)
    assertEquals(FakeSession(2), successful.session)
    assertEquals(1, runtimes.size)
  }

  @Test
  fun failedSessionCreationClosesTheOpenedRuntime() = runTest {
    val closedRuntimeIds = mutableListOf<Int>()
    val coordinator =
      WebStartupCoordinator(
        openRuntime = { FakeRuntime(1) },
        createSession = { error("session failed") },
        closeRuntime = { runtime -> closedRuntimeIds += runtime.id },
      )

    val result = coordinator.start()

    assertEquals("session failed", assertIs<WebStartupResult.Failed>(result).message)
    assertEquals(listOf(1), closedRuntimeIds)
  }

  private data class FakeRuntime(val id: Int)

  private data class FakeSession(val runtimeId: Int)
}
