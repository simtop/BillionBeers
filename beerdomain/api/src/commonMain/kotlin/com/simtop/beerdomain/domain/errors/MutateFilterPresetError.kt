package com.simtop.beerdomain.domain.errors

sealed interface MutateFilterPresetError {
  data class Unknown(val cause: Throwable) : MutateFilterPresetError
}
