package com.simtop.beerdomain.domain.errors

sealed interface UpdateFavoriteError {
  data class Unknown(val cause: Throwable) : UpdateFavoriteError
}
