package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.BaseUseCase
import com.simtop.core.core.Either
import javax.inject.Inject

class UpdateAvailabilityUseCase @Inject constructor(private val beersRepository: BeersRepository) :
  BaseUseCase<Unit, UpdateAvailabilityUseCase.Params>() {

  inner class Params(val beer: Beer)

  override suspend fun buildUseCase(params: Params): Either<Exception, Unit> {
    try {
      beersRepository.updateAvailability(params.beer)
    } catch (exception: Exception) {
      return Either.Left(exception)
    }
    return Either.Right(Unit)
  }
}
