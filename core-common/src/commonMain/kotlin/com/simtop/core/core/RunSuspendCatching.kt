package com.simtop.core.core

import kotlinx.coroutines.CancellationException

/** Runs a suspending operation as a [Result] while keeping coroutine cancellation observable. */
@Suppress("TooGenericExceptionCaught")
suspend fun <T> runSuspendCatching(block: suspend () -> T): Result<T> =
  try {
    Result.success(block())
  } catch (error: CancellationException) {
    throw error
  } catch (error: Throwable) {
    Result.failure(error)
  }
