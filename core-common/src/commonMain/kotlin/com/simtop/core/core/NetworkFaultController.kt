package com.simtop.core.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkFaultMode {
  NONE,
  FORCE_404,
  FORCE_500,
  EXTRA_LATENCY,
}

interface NetworkFaultController {
  val mode: StateFlow<NetworkFaultMode>

  fun setMode(mode: NetworkFaultMode)
}

class DefaultNetworkFaultController : NetworkFaultController {
  private val _mode = MutableStateFlow(NetworkFaultMode.NONE)
  override val mode: StateFlow<NetworkFaultMode> = _mode.asStateFlow()

  override fun setMode(mode: NetworkFaultMode) {
    _mode.value = mode
  }
}
