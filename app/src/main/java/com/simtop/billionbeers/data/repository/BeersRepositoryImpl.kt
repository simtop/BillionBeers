package com.simtop.billionbeers.data.repository

import com.simtop.billionbeers.core.Either
import com.simtop.billionbeers.core.safeCall
import com.simtop.billionbeers.data.localsource.BeersLocalSource
import com.simtop.billionbeers.data.mappers.BeersMapper
import com.simtop.billionbeers.data.mappers.DataErrorsMapper.processErrors
import com.simtop.billionbeers.data.remotesources.BeersRemoteSource
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.domain.repository.BeersRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import java.io.IOException
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

    override suspend fun updateAvailability(beer: Beer) =
        beersLocalSource.updateBeer(beer.id, beer.availability)

    override suspend fun insertAllToDB(beers: List<Beer>) =
        beersLocalSource.insertAllToDB(beers.map { BeersMapper.fromBeerToBeerDbModel(it) })

    override suspend fun getAllBeersFromDB() =
        beersLocalSource.getAllBeersFromDB().map { BeersMapper.fromBeerDbModelToBeer(it) }

    override suspend fun countDBEntries() = beersLocalSource.getCountFromDB()

    override suspend fun getBeersFromSingleSource(quantity: Int): Flow<Either<Exception, List<Beer>>> {

        return safeCall {
            if (countDBEntries() == 0) {
                val apiResults = getQuantityOfBeerFromApi(quantity)
                insertAllToDB(apiResults)
            }
            getAllBeersFromDB()
        }
    }
}

