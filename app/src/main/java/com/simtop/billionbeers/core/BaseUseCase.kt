package com.simtop.billionbeers.core

import com.simtop.billionbeers.domain.models.Beer
import kotlinx.coroutines.flow.Flow

abstract class BaseUseCase<T, PARAMS> protected constructor() {

    protected abstract suspend fun buildUseCase(params: PARAMS): Either<Exception, T>

    suspend fun execute(params: PARAMS) = buildUseCase(params)
}
