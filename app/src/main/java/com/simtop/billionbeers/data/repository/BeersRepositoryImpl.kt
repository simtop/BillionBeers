package com.simtop.billionbeers.data.repository

import com.simtop.billionbeers.data.mappers.BeersMapper
import com.simtop.billionbeers.data.remotesources.BeersRemoteSource
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.domain.repository.BeersRepository
import javax.inject.Inject

class BeersRepositoryImpl @Inject constructor(
    private val beersRemoteSource: BeersRemoteSource
)  : BeersRepository {
    override suspend fun getListOfBeerModels(page: Int): List<Beer> =
        beersRemoteSource.getListOfBeers(page).map { BeersMapper.fromBeersApiResponseItemToBeer(it) }
}