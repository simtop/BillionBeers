package com.simtop.beerdomain.domain.errors

sealed interface FetchBeersError {
  data object Network : FetchBeersError

  data object NotFound : FetchBeersError

  data object Forbidden : FetchBeersError

  data class Unknown(val cause: Throwable) : FetchBeersError
}
