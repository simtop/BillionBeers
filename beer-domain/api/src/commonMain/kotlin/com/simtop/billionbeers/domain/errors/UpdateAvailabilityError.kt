package com.simtop.billionbeers.domain.errors

sealed interface UpdateAvailabilityError {
  data class Unknown(val cause: Throwable) : UpdateAvailabilityError
}
