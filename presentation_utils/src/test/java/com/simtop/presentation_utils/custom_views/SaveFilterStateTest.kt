package com.simtop.presentation_utils.custom_views

import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SaveFilterStateTest {
  @Test
  fun `failure retains draft and success clears and closes it`() = runTest {
    val state = SaveFilterState(initiallyExpanded = true, initialName = "My IPA")
    state.save { false }
    assertTrue(state.expanded)
    assertEquals("My IPA", state.name)
    assertFalse(state.saving)
    state.save { name ->
      assertEquals("My IPA", name)
      true
    }
    assertFalse(state.expanded)
    assertEquals("", state.name)
    assertFalse(state.saving)
  }

  @Test
  fun `in flight submission cannot be duplicated and cancellation releases busy state`() = runTest {
    val state = SaveFilterState(initiallyExpanded = true, initialName = "My IPA")
    val completion = CompletableDeferred<Boolean>()
    var attempts = 0
    val job = launch {
      state.save {
        attempts++
        completion.await()
      }
    }
    runCurrent()
    assertTrue(state.saving)
    state.save {
      attempts++
      true
    }
    assertEquals(1, attempts)
    job.cancel(CancellationException("leaving screen"))
    job.join()
    assertFalse(state.saving)
    assertTrue(state.expanded)
    assertEquals("My IPA", state.name)
    state.save { true }
    assertFalse(state.expanded)
  }

  @Test
  fun `closing keeps a draft and blank names never submit`() = runTest {
    val state = SaveFilterState(initiallyExpanded = true, initialName = "  ")
    state.save { error("Blank name must not be saved") }
    state.name = "My IPA"
    state.close()
    state.toggle()
    assertEquals("My IPA", state.name)
    assertTrue(state.expanded)
  }
}
