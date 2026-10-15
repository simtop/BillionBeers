package com.simtop.core.core

import kotlinx.coroutines.CancellationException

fun interface Logger {
  fun log(tag: LogTag, diagnostic: Diagnostic)
}

/**
 * Diagnostics must not turn a successful operation into a failure or replace its original error.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
fun Logger.logSafely(tag: LogTag, diagnostic: Diagnostic) {
  try {
    log(tag, diagnostic)
  } catch (e: CancellationException) {
    throw e
  } catch (_: Exception) {
    // Reporting is best-effort; do not recursively report a failing adapter.
  }
}

enum class LogTag(val value: String) {
  BEERS_MAPPER("BeersMapper"),
  BEERS_REMOTE_SOURCE("BeersRemoteSource"),
  BEERS_LOCAL_SOURCE("BeersLocalSource"),
}

class NoOpLogger : Logger {
  override fun log(tag: LogTag, diagnostic: Diagnostic) = Unit
}
