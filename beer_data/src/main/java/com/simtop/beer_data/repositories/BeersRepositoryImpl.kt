package com.simtop.beer_data.repositories

import com.simtop.beer_data.mappers.BeersMapper
import com.simtop.beer_database.localsources.BeersLocalSource
import com.simtop.beer_network.remotesources.BeersRemoteSource
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.PagingMediator
import javax.inject.Inject

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

import javax.inject.Singleton

@Singleton
class BeersRepositoryImpl @Inject constructor(
    private val beersRemoteSource: BeersRemoteSource,
    private val beersLocalSource: BeersLocalSource
) : BeersRepository {

    private val pagingMediator = PagingMediator<Int, Beer>(
        initialKey = 1,
        nextKey = { currentKey, _ -> currentKey + 1 },
        fetchRemote = { page ->
            val apiItems = beersRemoteSource.getListOfBeers(page)
            coroutineScope {
                apiItems.map { item ->
                    async {
                        val beer = BeersMapper.fromBeersApiResponseItemToBeer(item)
                        if (!item.imageId.isNullOrEmpty()) {
                            try {
                                val imageResponse = beersRemoteSource.getImage(item.imageId!!)
                                beer.copy(imageUrl = imageResponse.url)
                            } catch (e: Exception) {
                                beer
                            }
                        } else {
                            beer
                        }
                    }
                }.awaitAll()
            }
        },
        saveLocal = { beers ->
            beersLocalSource.insertAllToDB(beers.map { BeersMapper.fromBeerToBeerDbModel(it) })
        },
        fetchLocal = {
            beersLocalSource.getAllBeersFromDB()
                .map { list -> list.map { BeersMapper.fromBeerDbModelToBeer(it) } }
        }
    )

    override suspend fun getListOfBeerFromApi(page: Int): List<Beer> {
        val apiItems = beersRemoteSource.getListOfBeers(page)
        return coroutineScope {
            apiItems.map { item ->
                async {
                    val beer = BeersMapper.fromBeersApiResponseItemToBeer(item)
                    if (!item.imageId.isNullOrEmpty()) {
                        try {
                            val imageResponse = beersRemoteSource.getImage(item.imageId!!)
                            beer.copy(imageUrl = imageResponse.url)
                        } catch (e: Exception) {
                            beer
                        }
                    } else {
                        beer
                    }
                }
            }.awaitAll()
        }
    }

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