package com.simtop.beer_data.fakes

import com.simtop.beer_database.localsources.BeersLocalSource
import com.simtop.beer_database.models.BeerDbModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeBeersLocalSource : BeersLocalSource {

    private val beersFlow = MutableStateFlow<List<BeerDbModel>>(emptyList())

    // Helper to inspect state
    fun getBeers(): List<BeerDbModel> = beersFlow.value

    override fun getAllBeersFromDB(): Flow<List<BeerDbModel>> {
        return beersFlow
    }

    override suspend fun insertAllToDB(beers: List<BeerDbModel>) {
        // Simple append or replace logic?
        // Repository usually replaces or appends.
        // Let's assume append for simplicity or replace if ID matches?
        // For now, let's just add them to the list for testing "insert happened"
        val current = beersFlow.value.toMutableList()
        beers.forEach { newBeer ->
            val index = current.indexOfFirst { it.id == newBeer.id }
            if (index != -1) {
                current[index] = newBeer
            } else {
                current.add(newBeer)
            }
        }
        beersFlow.value = current
    }

    override fun updateBeer(primaryKey: String, availability: Boolean) {
        val current = beersFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == primaryKey }
        if (index != -1) {
            current[index] = current[index].copy(availability = availability)
            beersFlow.value = current
        }
    }

    override fun deleteAllFromDB() {
        beersFlow.value = emptyList()
    }

    override suspend fun getCountFromDB(): Int {
        return beersFlow.value.size
    }
}
