package com.simtop.beer_data.fakes

import com.simtop.beer_database.localsources.BeersLocalSource
import com.simtop.beer_database.models.BeerDbModel
import com.simtop.beer_database.models.PagingStateDbModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeBeersLocalSource : BeersLocalSource {

  private val beersFlow = MutableStateFlow<List<BeerDbModel>>(emptyList())
  private val pagingState = mutableMapOf<String, PagingStateDbModel>()

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

  // Mirrors BeersDao.insertPage: upsert the rows and merge the bookmark monotonically in one step.
  override suspend fun insertPageToDB(
    beers: List<BeerDbModel>,
    surface: String,
    nextKey: Int?,
    totalCount: Int?,
  ) {
    insertAllToDB(beers)
    val existing = pagingState[surface]
    pagingState[surface] =
      PagingStateDbModel(
        surface = surface,
        nextKey = listOfNotNull(existing?.nextKey, nextKey).maxOrNull(),
        totalCount = totalCount ?: existing?.totalCount,
        refreshedAt = 0L,
      )
  }

  override suspend fun getPagingState(surface: String): PagingStateDbModel? = pagingState[surface]

  override suspend fun countPagingStates(): Int = pagingState.size

  /** Test helper: seed a bookmark as a warm cache would leave it, with a steerable timestamp. */
  fun setPagingState(surface: String, nextKey: Int?, refreshedAt: Long) {
    pagingState[surface] =
      PagingStateDbModel(
        surface = surface,
        nextKey = nextKey,
        totalCount = null,
        refreshedAt = refreshedAt,
      )
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
