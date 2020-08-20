package com.simtop.billionbeers.domain.repository

import com.simtop.billionbeers.domain.models.Beer

interface BeersRepository {
    suspend fun countDBEntries(): Int
    suspend fun getAllBeersFromDB(): List<Beer>
    suspend fun insertAllToDB(beers: List<Beer>)
    suspend fun updateAvailability(beer: Beer)
    suspend fun getBeersFromSingleSource(quantity: Int): List<Beer>
    suspend fun getListOfBeerFromApi(page: Int): List<Beer>
    suspend fun getQuantityOfBeerFromApi(quantity: Int): List<Beer>
}