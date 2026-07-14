package com.simtop.beerdomain.domain.errors

sealed interface FetchBeersError {
  data object Network : FetchBeersError

  data object NotFound : FetchBeersError

  data object Forbidden : FetchBeersError

  /** HTTP 429: the backend is rate-limiting us. Recoverable - the same retry path applies. */
  data object RateLimited : FetchBeersError

  data class Unknown(val cause: Throwable) : FetchBeersError
}
