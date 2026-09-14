package com.simtop.core.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
  LIGHT,
  DARK,
  SYSTEM,
}

interface ThemeController {
  val mode: StateFlow<ThemeMode>

  fun setMode(mode: ThemeMode)
}

class DefaultThemeController : ThemeController {
  private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
  override val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

  override fun setMode(mode: ThemeMode) {
    _mode.value = mode
  }
}
