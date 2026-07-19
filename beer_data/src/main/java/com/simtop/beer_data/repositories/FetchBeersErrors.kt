package com.simtop.beer_data.repositories

import com.simtop.beerdomain.domain.errors.FetchBeersError
import java.io.IOException
import java.net.HttpURLConnection
import retrofit2.HttpException

// No HttpURLConnection constant exists for 429.
private const val HTTP_TOO_MANY_REQUESTS = 429

/** The one HTTP/IO → [FetchBeersError] mapping, shared by every beers fetch path. */
internal fun Throwable.toFetchBeersError(): FetchBeersError =
  when (this) {
    is HttpException ->
      when (code()) {
        HttpURLConnection.HTTP_NOT_FOUND -> FetchBeersError.NotFound
        HttpURLConnection.HTTP_FORBIDDEN -> FetchBeersError.Forbidden
        HTTP_TOO_MANY_REQUESTS -> FetchBeersError.RateLimited
        else -> FetchBeersError.Unknown(this)
      }
    is IOException -> FetchBeersError.Network
    else -> FetchBeersError.Unknown(this)
  }
