package com.simtop.beer_data.fakes

import com.simtop.beer_storage.api.BeersStorage
import com.simtop.beer_storage.api.FilterPresetCapacityReachedException
import com.simtop.beer_storage.api.StoredBeer
import com.simtop.beer_storage.api.StoredFilterPreset
import com.simtop.beer_storage.api.StoredPagingState
import com.simtop.beer_storage.api.MAX_STORED_FILTER_PRESETS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeBeersLocalSource : BeersStorage {

  private val beersFlow = MutableStateFlow<List<StoredBeer>>(emptyList())
  private val presetsFlow = MutableStateFlow<List<StoredFilterPreset>>(emptyList())
  private val pagingState = mutableMapOf<String, StoredPagingState>()
  var presetMutationFailure: Exception? = null

  // Helper to inspect state
  fun getBeers(): List<StoredBeer> = beersFlow.value

  override fun observeBeers(): Flow<List<StoredBeer>> = beersFlow

  override fun observeFavoriteBeers(): Flow<List<StoredBeer>> = beersFlow.map { beers ->
    beers.filter { it.isFavorite }.sortedWith(compareBy({ it.name }, { it.id }))
  }

  override fun observeSavedFilterPresets(): Flow<List<StoredFilterPreset>> = presetsFlow

  override suspend fun saveFilterPreset(preset: StoredFilterPreset) {
    presetMutationFailure?.let { throw it }
    require(preset.id.isNotBlank() && preset.name.isNotBlank())
    require(preset.name.length <= 64)
    val current = presetsFlow.value.toMutableList()
    val index = current.indexOfFirst { it.id == preset.id }
    if (index < 0 && current.size >= MAX_STORED_FILTER_PRESETS) throw FilterPresetCapacityReachedException()
    if (index < 0) current += preset else current[index] = preset
    presetsFlow.value = current.sortedWith(compareByDescending<StoredFilterPreset> { it.updatedAt }.thenBy { it.name }.thenBy { it.id })
  }

  override suspend fun renameFilterPreset(id: String, name: String, updatedAt: Long) {
    presetMutationFailure?.let { throw it }
    require(name.isNotBlank() && name.length <= 64)
    presetsFlow.value = presetsFlow.value.map { preset ->
      if (preset.id == id) preset.copy(name = name, updatedAt = updatedAt) else preset
    }.sortedWith(compareByDescending<StoredFilterPreset> { it.updatedAt }.thenBy { it.name }.thenBy { it.id })
  }

  override suspend fun deleteFilterPreset(id: String) {
    presetMutationFailure?.let { throw it }
    presetsFlow.value = presetsFlow.value.filterNot { it.id == id }
  }

  // Mirrors the real DAO's upsert: existing rows keep their local-only availability.
  override suspend fun insertAll(beers: List<StoredBeer>) {
    val current = beersFlow.value.toMutableList()
    beers.forEach { newBeer ->
      val index = current.indexOfFirst { it.id == newBeer.id }
      if (index != -1) {
        current[index] =
          newBeer.copy(
            availability = current[index].availability,
            isFavorite = current[index].isFavorite,
          )
      } else {
        current.add(newBeer)
      }
    }
    beersFlow.value = current
  }

  // Mirrors BeersDao.insertPage: upsert the rows and merge the bookmark monotonically in one step.
  override suspend fun insertPage(
    beers: List<StoredBeer>,
    surface: String,
    nextKey: Int?,
    totalCount: Int?,
  ) {
    insertAll(beers)
    val existing = pagingState[surface]
    pagingState[surface] =
      StoredPagingState(
        surface = surface,
        nextKey = listOfNotNull(existing?.nextKey, nextKey).maxOrNull(),
        totalCount = totalCount ?: existing?.totalCount,
        refreshedAt = 0L,
      )
  }

  override suspend fun getPagingState(surface: String): StoredPagingState? = pagingState[surface]

  override suspend fun countPagingStates(): Int = pagingState.size

  /** Test helper: seed a bookmark as a warm cache would leave it, with a steerable timestamp. */
  fun setPagingState(surface: String, nextKey: Int?, refreshedAt: Long) {
    pagingState[surface] =
      StoredPagingState(
        surface = surface,
        nextKey = nextKey,
        totalCount = null,
        refreshedAt = refreshedAt,
      )
  }

  // Mirrors BeersDao.upsertAvailability: update the cached row, insert the full row when absent.
  override suspend fun upsertAvailability(beer: StoredBeer) {
    val current = beersFlow.value.toMutableList()
    val index = current.indexOfFirst { it.id == beer.id }
    if (index != -1) {
      current[index] = current[index].copy(availability = beer.availability)
    } else {
      current.add(beer)
    }
    beersFlow.value = current
  }

  override suspend fun upsertFavorite(beer: StoredBeer) {
    val current = beersFlow.value.toMutableList()
    val index = current.indexOfFirst { it.id == beer.id }
    if (index != -1) {
      current[index] = current[index].copy(isFavorite = beer.isFavorite)
    } else {
      current.add(beer)
    }
    beersFlow.value = current
  }

  override suspend fun deleteAll() {
    beersFlow.value = emptyList()
  }

  override suspend fun count(): Int = beersFlow.value.size
}
