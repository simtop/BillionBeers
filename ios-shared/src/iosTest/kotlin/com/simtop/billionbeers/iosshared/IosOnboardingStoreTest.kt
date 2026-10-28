package com.simtop.billionbeers.iosshared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults

class IosOnboardingStoreTest {
  @Test
  fun defaultsSurviveReopeningTheAdapterAndReset() = runTest {
    val suite = "onboarding-test-${NSUUID().UUIDString}"
    val defaults = NSUserDefaults(suiteName = suite)
    try {
      assertEquals(emptySet(), IosOnboardingStore(defaults).read())
      IosOnboardingStore(defaults).write(setOf("welcome", "favorites", "future-introduction"))
      assertEquals(
        setOf("welcome", "favorites", "future-introduction"),
        IosOnboardingStore(NSUserDefaults(suiteName = suite)).read(),
      )
      IosOnboardingStore(defaults).write(emptySet())
      assertEquals(emptySet(), IosOnboardingStore(defaults).read())
    } finally {
      defaults.removePersistentDomainForName(suite)
    }
  }
}
