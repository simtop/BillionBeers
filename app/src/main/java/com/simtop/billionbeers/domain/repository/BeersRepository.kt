package com.simtop.billionbeers.domain.repository

import com.simtop.billionbeers.core.Either
import com.simtop.billionbeers.domain.models.Beer
import kotlinx.coroutines.flow.Flow

interface BeersRepository {
    suspend fun countDBEntries(): Int
    suspend fun getAllBeersFromDB(): List<Beer>
    suspend fun insertAllToDB(beers: List<Beer>)
    suspend fun updateAvailability(beer: Beer): Flow<Either<Exception, Unit>>
    suspend fun getBeersFromSingleSource(quantity: Int): Flow<Either<Exception, List<Beer>>>
    suspend fun getListOfBeerFromApi(page: Int): List<Beer>
    suspend fun getQuantityOfBeerFromApi(quantity: Int): List<Beer>
}