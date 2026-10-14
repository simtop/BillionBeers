package com.simtop.beer_data.repositories

import com.simtop.beer_network.network.BeersServiceHttpException
import com.simtop.beer_network.network.BeersServiceNetworkException
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.core.core.Diagnostic
import com.simtop.core.core.DiagnosticArea
import com.simtop.core.core.DiagnosticCode
import com.simtop.core.core.DiagnosticLevel
import com.simtop.core.core.LogPriority
import com.simtop.core.core.LogTag
import com.simtop.core.core.Logger
import kotlinx.serialization.SerializationException

private const val HTTP_NOT_FOUND = 404
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429
private val HTTP_SERVER_ERROR_RANGE = 500..599

/** The one HTTP/IO → [FetchBeersError] mapping, shared by every beers fetch path. */
internal fun Throwable.toFetchBeersError(logger: Logger): FetchBeersError {
  val error = when (this) {
    is BeersServiceHttpException ->
      when (statusCode) {
        HTTP_NOT_FOUND -> FetchBeersError.NotFound
        HTTP_FORBIDDEN -> FetchBeersError.Forbidden
        HTTP_TOO_MANY_REQUESTS -> FetchBeersError.RateLimited
        in HTTP_SERVER_ERROR_RANGE -> FetchBeersError.Server
        else -> FetchBeersError.Unknown(this)
      }
    is BeersServiceNetworkException -> FetchBeersError.Network
    is SerializationException -> FetchBeersError.Parse
    else -> FetchBeersError.Unknown(this)
  }

  val code =
    when (error) {
      FetchBeersError.Network -> DiagnosticCode.NETWORK_UNAVAILABLE
      FetchBeersError.NotFound -> DiagnosticCode.HTTP_NOT_FOUND
      FetchBeersError.Forbidden -> DiagnosticCode.HTTP_FORBIDDEN
      FetchBeersError.RateLimited -> DiagnosticCode.RATE_LIMITED
      FetchBeersError.Server -> DiagnosticCode.SERVER_FAILURE
      FetchBeersError.Parse -> DiagnosticCode.PARSE_FAILURE
      is FetchBeersError.Unknown -> DiagnosticCode.UNKNOWN
    }
  val level =
    when (error) {
      FetchBeersError.NotFound -> DiagnosticLevel.INFO
      FetchBeersError.Network,
      FetchBeersError.Forbidden,
      FetchBeersError.RateLimited -> DiagnosticLevel.WARNING
      FetchBeersError.Server,
      FetchBeersError.Parse,
      is FetchBeersError.Unknown -> DiagnosticLevel.ERROR
    }
  val priority =
    when (level) {
      DiagnosticLevel.INFO -> LogPriority.INFO
      DiagnosticLevel.WARNING -> LogPriority.WARN
      DiagnosticLevel.ERROR -> LogPriority.ERROR
    }
  val area =
    if (error == FetchBeersError.Parse) DiagnosticArea.DATA_MAPPING else DiagnosticArea.NETWORK
  logger.log(
    priority,
    LogTag.BEERS_REMOTE_SOURCE,
    Diagnostic(area = area, code = code, level = level),
  )
  return error
}
