package com.simtop.core.core

abstract class BaseUseCase<T, PARAMS> protected constructor() {

    protected abstract suspend fun buildUseCase(params: PARAMS): Either<Exception, T>

    suspend fun execute(params: PARAMS) = buildUseCase(params)
}