package com.simtop.billionbeers.domain.errors

sealed interface UpdateFavoriteError {
  data class Unknown(val cause: Throwable) : UpdateFavoriteError
}
