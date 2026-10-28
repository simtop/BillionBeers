package com.simtop.billionbeers.shared.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingControllerTest {
  @Test
  fun `fresh launch resolves before showing welcome and dismissal survives a new controller`() =
    runTest {
      val store = MemoryStore()
      val controller = OnboardingController(store, this)
      assertNull(controller.state.value)
      assertFalse(controller.claim(OnboardingIntroductions.favorites))
      runCurrent()
      assertEquals(OnboardingState(), controller.state.value)
      controller.complete(OnboardingIntroductions.welcome)
      controller.complete(OnboardingIntroductions.welcome)
      assertTrue(checkNotNull(controller.state.value).isComplete(OnboardingIntroductions.welcome))
      runCurrent()
      val nextLaunch = OnboardingController(store, this)
      runCurrent()
      assertTrue(checkNotNull(nextLaunch.state.value).isComplete(OnboardingIntroductions.welcome))
      assertEquals(1, store.writes)
    }

  @Test
  fun `hint can be claimed only once and remains consumed after relaunch`() = runTest {
    val store = MemoryStore(setOf(OnboardingIntroductions.welcome.value))
    val controller = OnboardingController(store, this)
    runCurrent()
    assertTrue(controller.claim(OnboardingIntroductions.favorites))
    assertFalse(controller.claim(OnboardingIntroductions.favorites))
    runCurrent()
    val nextLaunch = OnboardingController(store, this)
    runCurrent()
    assertFalse(nextLaunch.claim(OnboardingIntroductions.favorites))
  }

  @Test
  fun `completing one introduction leaves future introductions available across launches`() =
    runTest {
      val future = IntroductionId("new-feature")
      val store = MemoryStore()
      val controller = OnboardingController(store, this)
      runCurrent()
      controller.complete(OnboardingIntroductions.welcome)
      assertTrue(controller.claim(OnboardingIntroductions.favorites))
      runCurrent()
      val nextLaunch = OnboardingController(store, this)
      runCurrent()
      assertTrue(checkNotNull(nextLaunch.state.value).isComplete(OnboardingIntroductions.welcome))
      assertFalse(nextLaunch.claim(OnboardingIntroductions.favorites))
      assertTrue(nextLaunch.claim(future))
      assertFalse(nextLaunch.claim(future))
      runCurrent()
      assertEquals(setOf("welcome", "favorites", "new-feature"), store.saved)
    }

  @Test
  fun `read failures enter the app without showing a hint`() = runTest {
    val store = MemoryStore().apply { readFailure = IllegalStateException("unavailable") }
    val controller = OnboardingController(store, this)
    runCurrent()
    assertTrue(checkNotNull(controller.state.value).isComplete(OnboardingIntroductions.welcome))
    assertFalse(controller.claim(OnboardingIntroductions.favorites))
  }

  @Test
  fun `corrupted preference type enters the app`() = runTest {
    val store = MemoryStore().apply { readFailure = ClassCastException("wrong type") }
    val controller = OnboardingController(store, this)
    runCurrent()
    assertTrue(checkNotNull(controller.state.value).isComplete(OnboardingIntroductions.welcome))
  }

  @Test
  fun `write failure does not reopen welcome or repeat a hint during the session`() = runTest {
    val controller = OnboardingController(MemoryStore().apply { failWrite = true }, this)
    runCurrent()
    controller.complete(OnboardingIntroductions.welcome)
    assertTrue(controller.claim(OnboardingIntroductions.favorites))
    runCurrent()
    assertTrue(checkNotNull(controller.state.value).isComplete(OnboardingIntroductions.welcome))
    assertFalse(controller.claim(OnboardingIntroductions.favorites))
  }

  @Test
  fun `latest state wins when dismissal hint and reset writes are queued`() = runTest {
    val store = MemoryStore()
    val controller = OnboardingController(store, this)
    runCurrent()
    controller.complete(OnboardingIntroductions.welcome)
    controller.claim(OnboardingIntroductions.favorites)
    controller.reset()
    runCurrent()
    assertEquals(emptySet<String>(), store.saved)
  }

  @Test
  fun `debug reset wins over a suspended initial read`() = runTest {
    val readResult = CompletableDeferred<Set<String>>()
    var saved: Set<String>? = null
    val controller =
      OnboardingController(
        object : OnboardingStore {
          override suspend fun read() = readResult.await()

          override suspend fun write(completedIds: Set<String>) {
            saved = completedIds
          }
        },
        this,
      )
    runCurrent()
    controller.reset()
    readResult.complete(
      setOf(OnboardingIntroductions.welcome.value, OnboardingIntroductions.favorites.value)
    )
    runCurrent()
    assertEquals(OnboardingState(), controller.state.value)
    assertEquals(emptySet<String>(), saved)
  }

  @Test
  fun `reset before initial read starts is retained`() = runTest {
    val controller =
      OnboardingController(
        MemoryStore(
          setOf(OnboardingIntroductions.welcome.value, OnboardingIntroductions.favorites.value)
        ),
        this,
      )
    controller.reset()
    runCurrent()
    assertEquals(OnboardingState(), controller.state.value)
  }

  @Test
  fun `cancellation does not publish a successful launch state`() = runTest {
    val store = MemoryStore().apply { readFailure = kotlinx.coroutines.CancellationException() }
    val controller = OnboardingController(store, this)
    runCurrent()
    assertNull(controller.state.value)
  }

  private class MemoryStore(var saved: Set<String> = emptySet()) : OnboardingStore {
    var readFailure: Exception? = null
    var failWrite = false
    var writes = 0

    override suspend fun read(): Set<String> {
      readFailure?.let { throw it }
      return saved
    }

    override suspend fun write(completedIds: Set<String>) {
      check(!failWrite) { "unavailable" }
      writes++
      saved = completedIds
    }
  }
}
