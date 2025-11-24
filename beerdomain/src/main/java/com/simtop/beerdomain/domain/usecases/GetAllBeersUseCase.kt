package com.simtop.beerdomain.domain.usecases

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import javax.inject.Inject

class GetAllBeersUseCase @Inject constructor(private val beersRepository: BeersRepository) {

    data class Params(val quantity: Int)

    fun execute(params: Params): kotlinx.coroutines.flow.Flow<List<Beer>> {
        return beersRepository.getBeersFromSingleSource(params.quantity)
    }
}