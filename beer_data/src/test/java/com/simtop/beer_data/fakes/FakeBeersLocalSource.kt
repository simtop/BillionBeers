package com.simtop.beer_data.fakes

import com.simtop.beer_database.localsources.BeersLocalSource
import com.simtop.beer_database.models.BeerDbModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeBeersLocalSource : BeersLocalSource {

  private val beersFlow = MutableStateFlow<List<BeerDbModel>>(emptyList())

  // Helper to inspect state
  fun getBeers(): List<BeerDbModel> = beersFlow.value

  override fun getAllBeersFromDB(): Flow<List<BeerDbModel>> {
    return beersFlow
  }

  // Mirrors the real DAO's upsert: existing rows keep their local-only availability.
  override suspend fun insertAllToDB(beers: List<BeerDbModel>) {
    val current = beersFlow.value.toMutableList()
    beers.forEach { newBeer ->
      val index = current.indexOfFirst { it.id == newBeer.id }
      if (index != -1) {
        current[index] = newBeer.copy(availability = current[index].availability)
      } else {
        current.add(newBeer)
      }
    }
    beersFlow.value = current
  }

  override suspend fun updateBeer(primaryKey: String, availability: Boolean) {
    val current = beersFlow.value.toMutableList()
    val index = current.indexOfFirst { it.id == primaryKey }
    if (index != -1) {
      current[index] = current[index].copy(availability = availability)
      beersFlow.value = current
    }
  }

  override suspend fun deleteAllFromDB() {
    beersFlow.value = emptyList()
  }

  override suspend fun getCountFromDB(): Int {
    return beersFlow.value.size
  }
}
