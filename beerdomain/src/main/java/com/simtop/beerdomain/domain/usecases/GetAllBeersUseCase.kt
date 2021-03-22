package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import javax.inject.Inject

class GetAllBeersUseCase @Inject constructor(private val beersRepository: BeersRepository) :
    com.simtop.core.core.BaseUseCase<List<Beer>, GetAllBeersUseCase.Params>() {

    inner class Params(val quantity: Int)

    override suspend fun buildUseCase(params: Params): com.simtop.core.core.Either<Exception, List<Beer>> {
        val response = try {
            beersRepository.getBeersFromSingleSource(params.quantity)
        } catch (exception: Exception) {
            return com.simtop.core.core.Either.Left(exception)
        }
        return com.simtop.core.core.Either.Right(response)
    }
}