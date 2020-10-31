package com.simtop.billionbeers.core

import com.simtop.billionbeers.domain.models.Beer
import kotlinx.coroutines.flow.Flow

abstract class FlowBaseUseCase<T, PARAMS> protected constructor() {

    protected abstract suspend fun buildUseCase(params: PARAMS): Flow<Either<Exception, List<Beer>>>

    suspend fun execute(params: PARAMS) = buildUseCase(params)
}