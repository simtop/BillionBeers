package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.PagingState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObservePagingStateUseCase @Inject constructor(private val beersRepository: BeersRepository) {
  fun execute(): Flow<PagingState> = beersRepository.observePagingState()
}
