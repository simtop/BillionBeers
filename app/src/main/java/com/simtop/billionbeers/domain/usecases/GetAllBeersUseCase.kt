package com.simtop.billionbeers.domain.usecases

import com.simtop.billionbeers.core.BaseUseCase
import com.simtop.billionbeers.core.Either
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.domain.repository.BeersRepository
import retrofit2.HttpException
import javax.inject.Inject

class GetAllBeersUseCase @Inject constructor(private val beersRepository: BeersRepository) :
    BaseUseCase<List<Beer>, GetAllBeersUseCase.Params>() {

    inner class Params(val quantity: Int)

    override suspend fun buildUseCase(params: Params): Either<Exception, List<Beer>> {
           return beersRepository.getBeersFromSingleSource(params.quantity)
    }
}