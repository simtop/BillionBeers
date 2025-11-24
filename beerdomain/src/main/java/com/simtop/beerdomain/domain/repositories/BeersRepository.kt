package com.simtop.beerdomain.domain.repositories

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.core.core.PagingState
import kotlinx.coroutines.flow.Flow

interface BeersRepository {
    suspend fun countDBEntries(): Int
    suspend fun getAllBeersFromDB(): List<Beer>
    suspend fun insertAllToDB(beers: List<Beer>)
    suspend fun updateAvailability(beer: Beer)
    fun getBeersFromSingleSource(quantity: Int): Flow<List<Beer>>
    fun observePagingState(): Flow<PagingState>
    suspend fun loadNextPage()

    suspend fun getListOfBeerFromApi(page: Int): List<Beer>
    suspend fun getQuantityOfBeerFromApi(quantity: Int): List<Beer>
}