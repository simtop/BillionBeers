package com.simtop.beer_storage.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

const val MAX_STORED_FILTER_PRESETS = 10

class FilterPresetCapacityReachedException :
  IllegalArgumentException("At most $MAX_STORED_FILTER_PRESETS filter presets may be saved")

/**
 * Portable persistence contract for the beer catalog. Implementations must emit only committed
 * state, preserve local-only flags during catalog refreshes, and write a page with its bookmark as
 * one atomic operation. Since catalog rows are shared by language, writing a `catalog:<language>`
 * page also invalidates bookmarks for other catalog languages.
 */
interface BeersStorage {
  fun observeBeers(): Flow<List<StoredBeer>>

  /**
   * Observes local-only fields for the requested IDs. Backends with indexed reads should override
   * this fallback, which scans their full beer observation.
   */
  fun observeLocalFlags(ids: Set<String>): Flow<List<StoredBeerLocalFlags>> =
    if (ids.isEmpty()) flowOf(emptyList())
    else {
      observeBeers().map { beers ->
        beers.asSequence()
          .filter { it.id in ids }
          .map { StoredBeerLocalFlags(it.id, it.availability, it.isFavorite) }
          .toList()
      }
    }

  /** Reads one cached row by its primary key without materializing the full catalog. */
  suspend fun getBeerById(id: String): StoredBeer?

  /** Catalog-only view; storage backends without surface membership retain all rows. */
  fun observeCatalogBeers(): Flow<List<StoredBeer>> = observeBeers()

  fun observeSavedFilterPresets(): Flow<List<StoredFilterPreset>>

  suspend fun saveFilterPreset(preset: StoredFilterPreset)

  suspend fun renameFilterPreset(id: String, name: String, updatedAt: Long)

  suspend fun deleteFilterPreset(id: String)

  fun observeFavoriteBeers(): Flow<List<StoredBeer>>

  suspend fun insertAll(beers: List<StoredBeer>)

  /**
   * Persists fetched query rows for detail lookup. Implementations preserve local flags and existing
   * catalog membership/positions, give new rows no catalog membership, and leave paging bookmarks
   * unchanged.
   */
  suspend fun cacheBeersForDetail(beers: List<StoredBeer>)

  suspend fun insertPage(
    beers: List<StoredBeer>,
    surface: String,
    nextKey: Int?,
    totalCount: Int?,
    pageKey: Int? = null,
  )

  suspend fun getPagingState(surface: String): StoredPagingState?

  suspend fun countPagingStates(): Int

  suspend fun upsertAvailability(beer: StoredBeer)

  suspend fun upsertFavorite(beer: StoredBeer)

  suspend fun deleteAll()

  suspend fun count(): Int

  /** Number of rows available to the full catalog pager. */
  suspend fun countCatalogEntries(): Int = count()
}

data class StoredBeerLocalFlags(
  val id: String,
  val availability: Boolean,
  val isFavorite: Boolean,
)

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

data class StoredFilterPreset(
  val id: String,
  val name: String,
  val search: String?,
  val styleId: String?,
  val breweryId: String?,
  val updatedAt: Long,
)

data class StoredPagingState(
  val surface: String,
  val nextKey: Int?,
  val totalCount: Int?,
  val refreshedAt: Long,
  val isComplete: Boolean = false,
)
