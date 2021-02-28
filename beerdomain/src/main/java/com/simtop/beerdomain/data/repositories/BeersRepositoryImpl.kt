package com.simtop.beerdomain.data.repositories

import com.simtop.beerdomain.data.localsources.BeersLocalSource
import com.simtop.beerdomain.data.mappers.BeersMapper
import com.simtop.beerdomain.data.remotesources.BeersRemoteSource
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import javax.inject.Inject

class BeersRepositoryImpl @Inject constructor(
    private val beersRemoteSource: BeersRemoteSource,
    private val beersLocalSource: BeersLocalSource
) : BeersRepository {
    override suspend fun getListOfBeerFromApi(page: Int): List<Beer> =
        beersRemoteSource.getListOfBeers(page)
            .map { BeersMapper.fromBeersApiResponseItemToBeer(it) }

    override suspend fun getQuantityOfBeerFromApi(quantity: Int): List<Beer> {
        val totalList = mutableListOf<Beer>()
        for (page in 1..quantity) {
            totalList.addAll(getListOfBeerFromApi(page))
        }
        return totalList
    }

    //TODO: Remeber to do !beer.availability
    override suspend fun updateAvailability(beer: Beer) = beersLocalSource.updateBeer(beer.id, beer.availability)

    override suspend fun insertAllToDB(beers: List<Beer>) =
        beersLocalSource.insertAllToDB(beers.map { BeersMapper.fromBeerToBeerDbModel(it) })

    override suspend fun getAllBeersFromDB() =
        beersLocalSource.getAllBeersFromDB().map { BeersMapper.fromBeerDbModelToBeer(it) }

    override suspend fun countDBEntries() = beersLocalSource.getCountFromDB()

    override suspend fun getBeersFromSingleSource(quantity: Int): List<Beer> {
        if (countDBEntries() == 0) {
            val apiResults = getQuantityOfBeerFromApi(quantity)
            insertAllToDB(apiResults)
        }
        return getAllBeersFromDB()
    }
}