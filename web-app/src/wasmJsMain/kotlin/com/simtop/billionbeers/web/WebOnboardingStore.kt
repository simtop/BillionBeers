@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.simtop.billionbeers.web

import com.simtop.billionbeers.shared.presentation.OnboardingStore
import kotlin.JsFun
import kotlinx.serialization.json.Json

/** Origin-scoped preferences only. Beer data remains in IndexedDB. Storage denial fails open. */
internal class WebOnboardingStore(
  private val key: String = "billionbeers.completed_introduction_ids"
) : OnboardingStore {
  override suspend fun read(): Set<String> =
    Json.decodeFromString(
      checkNotNull(readOnboardingPreference(key)) { "Onboarding storage unavailable" }
    )

  override suspend fun write(completedIds: Set<String>) {
    check(writeOnboardingPreference(key, Json.encodeToString(completedIds))) {
      "Could not save onboarding preferences"
    }
  }
}

@JsFun("(key) => { try { return localStorage.getItem(key) || '[]'; } catch (_) { return null; } }")
private external fun readOnboardingPreference(key: String): String?

@JsFun(
  "(key, value) => { try { localStorage.setItem(key, value); return true; } catch (_) { return false; } }"
)
private external fun writeOnboardingPreference(key: String, value: String): Boolean
