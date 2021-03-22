package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import javax.inject.Inject

class UpdateAvailabilityUseCase @Inject constructor(private val beersRepository: BeersRepository) :
    com.simtop.core.core.BaseUseCase<Unit, UpdateAvailabilityUseCase.Params>() {

    inner class Params(val beer: Beer)

    override suspend fun buildUseCase(params: Params): com.simtop.core.core.Either<Exception, Unit> {
        try {
            beersRepository.updateAvailability(params.beer)
        } catch (exception: Exception) {
            return com.simtop.core.core.Either.Left(exception)
        }
        return com.simtop.core.core.Either.Right(Unit)
    }
}