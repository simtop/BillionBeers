package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.domain.repositories.BeersRepository
import javax.inject.Inject

class RefreshBeersUseCase @Inject constructor(private val beersRepository: BeersRepository) {
  suspend fun execute() {
    beersRepository.refresh()
  }
}
