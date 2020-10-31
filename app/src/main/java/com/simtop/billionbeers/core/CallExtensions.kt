package com.simtop.billionbeers.core

import com.simtop.billionbeers.data.mappers.DataErrorsMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException

suspend fun <T> safeCall(
    defaultMessage: String = "Default Error ",
    allowRetries: Boolean = true,
    numberOfRetries: Int = 2,
    call: suspend () -> T
): Flow<Either<Exception, T>> {
    var delayDuration = 1000L
    val delayFactor = 2
    return flow {
        try {
            emit(Either.Right(call.invoke()))
        } catch (exception: Exception) {
            emit(Either.Left(DataErrorsMapper.processErrors(exception, defaultMessage)))
        }
        return@flow
    }.catch { exception ->
        emit(Either.Left(DataErrorsMapper.processErrors(exception as Exception, defaultMessage)))
        return@catch
    }.retryWhen { cause, attempt ->
        if (!allowRetries || attempt > numberOfRetries || cause !is IOException) return@retryWhen false
        delay(delayDuration)
        delayDuration *= delayFactor
        return@retryWhen true
    }
}