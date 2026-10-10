package com.simtop.beerdomain.domain.errors

sealed interface MutateFilterPresetError {
  data object InvalidName : MutateFilterPresetError

  data class Unknown(val cause: Throwable) : MutateFilterPresetError
}
