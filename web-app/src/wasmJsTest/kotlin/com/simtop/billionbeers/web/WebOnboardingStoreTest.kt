@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.simtop.billionbeers.web

import kotlin.JsFun
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class WebOnboardingStoreTest {
  @Test
  fun browserPreferencesSurviveReopenAndReset() = runTest {
    val key = "onboarding-test-${Random.nextLong()}"
    try {
      assertEquals(emptySet(), WebOnboardingStore(key).read())
      WebOnboardingStore(key).write(setOf("welcome", "favorites", "future-introduction"))
      assertEquals(
        setOf("welcome", "favorites", "future-introduction"),
        WebOnboardingStore(key).read(),
      )
      WebOnboardingStore(key).write(emptySet())
      assertEquals(emptySet(), WebOnboardingStore(key).read())
      corruptPreference(key)
      assertFailsWith<IllegalArgumentException> { WebOnboardingStore(key).read() }
    } finally {
      removePreference(key)
    }
  }
}

@JsFun("(key) => localStorage.setItem(key, '{invalid json')")
private external fun corruptPreference(key: String)

@JsFun("(key) => localStorage.removeItem(key)") private external fun removePreference(key: String)
