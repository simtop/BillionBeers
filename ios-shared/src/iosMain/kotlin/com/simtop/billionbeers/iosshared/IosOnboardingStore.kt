package com.simtop.billionbeers.iosshared

import com.simtop.billionbeers.shared.presentation.OnboardingStore
import platform.Foundation.NSUserDefaults

internal class IosOnboardingStore(
  private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : OnboardingStore {
  override suspend fun read(): Set<String> {
    val stored = defaults.objectForKey(KEY) ?: return emptySet()
    check(stored is List<*>) { "Invalid onboarding preferences" }
    return stored.map { checkNotNull(it as? String) { "Invalid introduction ID" } }.toSet()
  }

  override suspend fun write(completedIds: Set<String>) {
    defaults.setObject(completedIds.sorted(), forKey = KEY)
  }

  private companion object {
    const val KEY = "completed_introduction_ids"
  }
}
