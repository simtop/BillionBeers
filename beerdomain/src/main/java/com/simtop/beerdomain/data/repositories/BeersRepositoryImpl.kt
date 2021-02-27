package com.simtop.beerdomain.data.repositories

import com.simtop.beerdomain.data.localsources.BeersLocalSource
import com.simtop.beerdomain.data.mappers.BeersMapper
import com.simtop.beerdomain.data.remotesources.BeersRemoteSource
import javax.inject.Inject

class BeersRepositoryImpl @Inject constructor(
    private val beersRemoteSource: BeersRemoteSource,
    private val beersLocalSource: BeersLocalSource
) : com.simtop.beerdomain.domain.repositories.BeersRepository {
    override suspend fun getListOfBeerFromApi(page: Int): List<com.simtop.beerdomain.domain.models.Beer> =
        beersRemoteSource.getListOfBeers(page)
            .map { BeersMapper.fromBeersApiResponseItemToBeer(it) }

    override suspend fun getQuantityOfBeerFromApi(quantity: Int): List<com.simtop.beerdomain.domain.models.Beer> {
        val totalList = mutableListOf<com.simtop.beerdomain.domain.models.Beer>()
        for (page in 1..quantity) {
            totalList.addAll(getListOfBeerFromApi(page))
        }
        return totalList
    }

    //TODO: Remeber to do !beer.availability
    override suspend fun updateAvailability(beer: com.simtop.beerdomain.domain.models.Beer) = beersLocalSource.updateBeer(beer.id, beer.availability)

    override suspend fun insertAllToDB(beers: List<com.simtop.beerdomain.domain.models.Beer>) =
        beersLocalSource.insertAllToDB(beers.map { BeersMapper.fromBeerToBeerDbModel(it) })

    override suspend fun getAllBeersFromDB() =
        beersLocalSource.getAllBeersFromDB().map { BeersMapper.fromBeerDbModelToBeer(it) }

    override suspend fun countDBEntries() = beersLocalSource.getCountFromDB()

    override suspend fun getBeersFromSingleSource(quantity: Int): List<com.simtop.beerdomain.domain.models.Beer> {
        if (countDBEntries() == 0) {
            val apiResults = getQuantityOfBeerFromApi(quantity)
            insertAllToDB(apiResults)
        }
        return getAllBeersFromDB()
    }
}