package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.domain.repositories.BeersRepository
import dev.zacsweers.metro.Inject

class LoadNextPageUseCase @Inject constructor(private val beersRepository: BeersRepository) {
  suspend fun execute() = beersRepository.loadNextPage()
}
