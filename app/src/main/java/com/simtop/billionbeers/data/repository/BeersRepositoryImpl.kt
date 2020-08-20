package com.simtop.billionbeers.data.repository

import com.simtop.billionbeers.data.database.BeersDao
import com.simtop.billionbeers.data.mappers.BeersMapper
import com.simtop.billionbeers.data.remotesources.BeersRemoteSource
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.domain.repository.BeersRepository
import javax.inject.Inject

class BeersRepositoryImpl @Inject constructor(
    private val beersRemoteSource: BeersRemoteSource,
    private val dao: BeersDao
) : BeersRepository {
    override suspend fun getListOfBeerFromApi(page: Int): List<Beer> =
        beersRemoteSource.getListOfBeers(page)
            .map { BeersMapper.fromBeersApiResponseItemToBeer(it) }

    override suspend fun getQuantityOfBeerFromApi(quantity: Int): List<Beer> {
        lateinit var totalList: MutableList<Beer>
        for (page in 1..quantity) {
            totalList.addAll(getListOfBeerFromApi(page))
        }
        return totalList.toList()
    }

    //TODO: Remeber to do !beer.availability
    override suspend fun updateAvailability(beer: Beer) = dao.updateBeer(beer.id, beer.availability)

    override suspend fun insertAllToDB(beers: List<Beer>) =
        dao.insertAll(beers.map { BeersMapper.fromBeerToBeerDbModel(it) })

    override suspend fun getAllBeersFromDB() =
        dao.getAllBeers().map { BeersMapper.fromBeerDbModelToBeer(it) }

    override suspend fun countDBEntries() = dao.getCount()

    override suspend fun getBeersFromSingleSource(quantity: Int): List<Beer> {
        if (countDBEntries() == 0) {
            val apiResults = getQuantityOfBeerFromApi(quantity)
            insertAllToDB(apiResults)
        }
        return getAllBeersFromDB()
    }

}