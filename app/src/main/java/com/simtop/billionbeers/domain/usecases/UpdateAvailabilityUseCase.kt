package com.simtop.billionbeers.domain.usecases

import com.simtop.billionbeers.core.BaseUseCase
import com.simtop.billionbeers.core.Either
import com.simtop.billionbeers.core.FlowBaseUseCase
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.domain.repository.BeersRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class UpdateAvailabilityUseCase @Inject constructor(private val beersRepository: BeersRepository) :
    FlowBaseUseCase<Unit, UpdateAvailabilityUseCase.Params>() {

    inner class Params(val beer: Beer)

    override suspend fun buildUseCase(params: Params): Flow<Either<Exception, Unit>>  {
            return beersRepository.updateAvailability(params.beer)
    }
}