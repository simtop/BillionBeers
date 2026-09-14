package com.simtop.beerdomain.domain.errors

sealed interface UpdateAvailabilityError {
  data class Unknown(val cause: Throwable) : UpdateAvailabilityError
}
