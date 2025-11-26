package com.simtop.beer_data.repositories

import com.simtop.beer_data.mappers.BeersMapper
import com.simtop.beer_database.localsources.BeersLocalSource
import com.simtop.beer_network.remotesources.BeersRemoteSourceContract
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.PagingMediator
import javax.inject.Inject

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart

import javax.inject.Singleton

@Singleton
class BeersRepositoryImpl @Inject constructor(
    private val beersRemoteSource: BeersRemoteSourceContract,
    private val beersLocalSource: BeersLocalSource
) : BeersRepository {

    private val pagingMediator = PagingMediator<Int, Beer>(
        initialKey = 1,
        nextKey = { currentKey, _ -> currentKey + 1 },
        fetchRemote = { page ->
            beersRemoteSource.getListOfBeers(page)
                .map { BeersMapper.fromBeersApiResponseItemToBeer(it) }
        },
        saveLocal = { beers ->
            beersLocalSource.insertAllToDB(beers.map { BeersMapper.fromBeerToBeerDbModel(it) })
        },
        fetchLocal = {
            beersLocalSource.getAllBeersFromDB()
                .map { list -> list.map { BeersMapper.fromBeerDbModelToBeer(it) } }
        }
    )

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
        beersLocalSource.getAllBeersFromDB().first().map { BeersMapper.fromBeerDbModelToBeer(it) }

    override suspend fun countDBEntries() = beersLocalSource.getCountFromDB()

    override fun getBeersFromSingleSource(quantity: Int): Flow<List<Beer>> {
        return pagingMediator.data.onStart {
             if (countDBEntries() == 0) {
                 pagingMediator.loadFirstPage()
             }
        }
    }

    override fun observePagingState() = pagingMediator.pagingState

    override suspend fun loadNextPage() {
        pagingMediator.loadNextPage()
    }
    
    // Helper to trigger initial load if needed
    suspend fun refresh() {
        pagingMediator.loadFirstPage()
    }
}