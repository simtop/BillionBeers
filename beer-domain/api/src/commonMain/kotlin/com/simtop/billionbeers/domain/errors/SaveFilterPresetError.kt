package com.simtop.billionbeers.domain.errors

sealed interface SaveFilterPresetError {
  data object InvalidName : SaveFilterPresetError

  data object CapacityReached : SaveFilterPresetError

  data class Unknown(val cause: Throwable) : SaveFilterPresetError
}
