package com.simtop.beerdomain.domain

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

class GetAllBeersUseCase @Inject constructor(private val beersRepository: BeersRepository) {

  fun execute(): Flow<List<Beer>> {
    return beersRepository.getBeersFromSingleSource()
  }
}
