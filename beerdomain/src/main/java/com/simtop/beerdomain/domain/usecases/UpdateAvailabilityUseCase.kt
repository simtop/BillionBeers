package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import javax.inject.Inject

class UpdateAvailabilityUseCase @Inject constructor(private val beersRepository: BeersRepository) :
    com.simtop.beerdomain.core.BaseUseCase<Unit, UpdateAvailabilityUseCase.Params>() {

    inner class Params(val beer: Beer)

    override suspend fun buildUseCase(params: Params): com.simtop.beerdomain.core.Either<Exception, Unit> {
        try {
            beersRepository.updateAvailability(params.beer)
        } catch (exception: Exception) {
            return com.simtop.beerdomain.core.Either.Left(exception)
        }
        return com.simtop.beerdomain.core.Either.Right(Unit)
    }
}