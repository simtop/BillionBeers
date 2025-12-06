package com.simtop.beerdomain.domain

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetAllBeersUseCase @Inject constructor(private val beersRepository: BeersRepository) {

  data class Params(val quantity: Int)

  fun execute(params: Params): Flow<List<Beer>> {
    return beersRepository.getBeersFromSingleSource(params.quantity)
  }
}
