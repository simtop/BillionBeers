package com.simtop.beer_storage.api

import kotlinx.coroutines.flow.Flow

/**
 * Portable persistence contract for the beer catalog. Implementations must emit only committed
 * state, preserve local-only flags during catalog refreshes, and write a page with its bookmark as
 * one atomic operation.
 */
interface BeersStorage {
  fun observeBeers(): Flow<List<StoredBeer>>

  fun observeFavoriteBeers(): Flow<List<StoredBeer>>

  suspend fun insertAll(beers: List<StoredBeer>)

  suspend fun insertPage(
    beers: List<StoredBeer>,
    surface: String,
    nextKey: Int?,
    totalCount: Int?,
  )

  suspend fun getPagingState(surface: String): StoredPagingState?

  suspend fun countPagingStates(): Int

  suspend fun upsertAvailability(beer: StoredBeer)

  suspend fun upsertFavorite(beer: StoredBeer)

  suspend fun deleteAll()

  suspend fun count(): Int
}

data class StoredBeer(
  val id: String,
  val name: String,
  val tagline: String,
  val description: String,
  val imageUrl: String,
  val abv: Double,
  val ibu: Double,
  val foodPairing: List<String>,
  val availability: Boolean = true,
  val isFavorite: Boolean = false,
  val styleName: String = "",
  val breweryName: String = "",
  val srm: Int? = null,
  val releasedYear: Int? = null,
  val minServingTemperature: Int? = null,
  val maxServingTemperature: Int? = null,
  val fermentationMethod: String = "",
  val ingredients: List<String> = emptyList(),
  val recommendedGlasses: List<String> = emptyList(),
)

data class StoredPagingState(
  val surface: String,
  val nextKey: Int?,
  val totalCount: Int?,
  val refreshedAt: Long,
)
