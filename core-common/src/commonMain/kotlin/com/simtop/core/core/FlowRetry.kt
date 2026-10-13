package com.simtop.core.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen

const val FLOW_RETRY_DELAY_MILLIS = 1_000L

/** Emits a caller-owned error state, waits briefly, and re-subscribes after a flow failure. */
fun <T> Flow<T>.retryWithErrorState(errorState: (Throwable) -> T): Flow<T> =
  retryWhen { cause, _ ->
    if (cause !is Exception) throw cause
    emit(errorState(cause))
    delay(FLOW_RETRY_DELAY_MILLIS)
    true
  }
