package com.simtop.core.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Placeholder flag set - a real project grows this enum as flags are added. The remote
 * (Firebase Remote Config) implementation of [FeatureFlagProvider] is a later addition; this one
 * is local-override-only, driven by the debug drawer.
 */
enum class FeatureFlag(val key: String, val displayName: String, val defaultValue: Boolean) {
  SHOW_DEBUG_BANNER("show_debug_banner", "Show Debug Banner", false)
}

interface FeatureFlagProvider {
  val overrides: StateFlow<Map<FeatureFlag, Boolean>>

  fun isEnabled(flag: FeatureFlag): Boolean

  /** Pass `null` to clear the override and fall back to [FeatureFlag.defaultValue]. */
  fun setOverride(flag: FeatureFlag, enabled: Boolean?)
}

class LocalFeatureFlagProvider : FeatureFlagProvider {
  private val _overrides = MutableStateFlow<Map<FeatureFlag, Boolean>>(emptyMap())
  override val overrides: StateFlow<Map<FeatureFlag, Boolean>> = _overrides.asStateFlow()

  override fun isEnabled(flag: FeatureFlag): Boolean = _overrides.value[flag] ?: flag.defaultValue

  override fun setOverride(flag: FeatureFlag, enabled: Boolean?) {
    _overrides.update { current -> if (enabled == null) current - flag else current + (flag to enabled) }
  }
}
