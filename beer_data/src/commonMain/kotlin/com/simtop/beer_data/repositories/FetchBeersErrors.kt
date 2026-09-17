package com.simtop.beer_data.repositories

import com.simtop.beer_network.network.BeersServiceHttpException
import com.simtop.beer_network.network.BeersServiceNetworkException
import com.simtop.beerdomain.domain.errors.FetchBeersError

private const val HTTP_NOT_FOUND = 404
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429

/** The one HTTP/IO → [FetchBeersError] mapping, shared by every beers fetch path. */
internal fun Throwable.toFetchBeersError(): FetchBeersError =
  when (this) {
    is BeersServiceHttpException ->
      when (statusCode) {
        HTTP_NOT_FOUND -> FetchBeersError.NotFound
        HTTP_FORBIDDEN -> FetchBeersError.Forbidden
        HTTP_TOO_MANY_REQUESTS -> FetchBeersError.RateLimited
        else -> FetchBeersError.Unknown(this)
      }
    is BeersServiceNetworkException -> FetchBeersError.Network
    else -> FetchBeersError.Unknown(this)
  }
