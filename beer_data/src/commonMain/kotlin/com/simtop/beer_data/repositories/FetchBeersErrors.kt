package com.simtop.beer_data.repositories

import com.simtop.beer_network.network.BeersServiceHttpException
import com.simtop.beer_network.network.BeersServiceNetworkException
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.core.core.Diagnostic
import com.simtop.core.core.DiagnosticArea
import com.simtop.core.core.DiagnosticCode
import com.simtop.core.core.DiagnosticLevel
import com.simtop.core.core.LogTag
import com.simtop.core.core.Logger
import com.simtop.core.core.logSafely
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

private const val HTTP_NOT_FOUND = 404
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR_MIN = 500
private const val HTTP_SERVER_ERROR_MAX = 599
private val HTTP_SERVER_ERROR_RANGE = HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX

/** The one HTTP/IO → [FetchBeersError] mapping, shared by every beers fetch path. */
internal fun Throwable.toFetchBeersError(): FetchBeersError =
  when (this) {
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

internal enum class FetchFailureOrigin(val tag: LogTag) {
  REMOTE(LogTag.BEERS_REMOTE_SOURCE),
  MAPPING(LogTag.BEERS_MAPPER),
  STORAGE(LogTag.BEERS_LOCAL_SOURCE),
}

/** Report where the origin is known; callers still own classification and recovery. */
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> withFetchDiagnostics(
  logger: Logger,
  origin: FetchFailureOrigin,
  operation: () -> T,
): T =
  try {
    operation()
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    logger.logSafely(origin.tag, e.toFetchDiagnostic(origin))
    throw e
  }

internal fun Throwable.toFetchDiagnostic(origin: FetchFailureOrigin): Diagnostic =
  when (origin) {
    FetchFailureOrigin.STORAGE ->
      Diagnostic(
        DiagnosticArea.DATABASE,
        DiagnosticCode.LOCAL_STORAGE_FAILURE,
        DiagnosticLevel.ERROR,
      )
    FetchFailureOrigin.MAPPING ->
      Diagnostic(
        DiagnosticArea.DATA_MAPPING,
        if (this is SerializationException) DiagnosticCode.PARSE_FAILURE
        else DiagnosticCode.UNKNOWN,
        DiagnosticLevel.ERROR,
      )
    FetchFailureOrigin.REMOTE -> toRemoteFetchDiagnostic()
  }

private fun Throwable.toRemoteFetchDiagnostic(): Diagnostic {
  val error = toFetchBeersError()
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
  val area =
    if (error == FetchBeersError.Parse) DiagnosticArea.DATA_MAPPING else DiagnosticArea.NETWORK
  return Diagnostic(area = area, code = code, level = level)
}
