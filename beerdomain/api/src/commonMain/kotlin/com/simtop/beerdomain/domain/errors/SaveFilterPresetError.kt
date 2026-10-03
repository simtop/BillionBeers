package com.simtop.beerdomain.domain.errors

sealed interface SaveFilterPresetError {
  data object CapacityReached : SaveFilterPresetError

  data class Unknown(val cause: Throwable) : SaveFilterPresetError
}
