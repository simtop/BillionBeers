package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.BaseUseCase
import com.simtop.core.core.Either
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