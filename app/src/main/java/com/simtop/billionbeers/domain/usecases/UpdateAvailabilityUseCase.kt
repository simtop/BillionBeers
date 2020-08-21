package com.simtop.billionbeers.domain.usecases

import com.simtop.billionbeers.core.BaseUseCase
import com.simtop.billionbeers.core.Either
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.domain.repository.BeersRepository
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