package com.simtop.billionbeers.domain.usecases

import com.simtop.billionbeers.core.BaseUseCase
import com.simtop.billionbeers.core.Either
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.domain.repository.BeersRepository
import javax.inject.Inject

class GetAllBeersUseCase @Inject constructor(private val beersRepository: BeersRepository) :
    BaseUseCase<List<Beer>, GetAllBeersUseCase.Params>() {

    inner class Params(val quantity: Int)

    override suspend fun buildUseCase(params: Params): Either<Exception, List<Beer>> {
        val response = try {
            beersRepository.getBeersFromSingleSource(params.quantity)
        } catch (exception: Exception) {
            return Either.Left(exception)
        }
        return Either.Right(response)
    }
}