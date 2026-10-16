package com.simtop.beer_database.localsources

import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_database.models.BeerDbModel
import com.simtop.beer_database.models.PagingStateDbModel
import com.simtop.beer_database.models.SavedFilterPresetDbModel
import com.simtop.beer_database.utils.Converters
import com.simtop.beer_storage.api.BeersStorage
import com.simtop.beer_storage.api.StoredBeer
import com.simtop.beer_storage.api.StoredFilterPreset
import com.simtop.beer_storage.api.StoredPagingState
import com.simtop.core.core.EpochTimeProvider
import com.simtop.core.core.SystemEpochTimeProvider
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val MAX_PRESET_NAME_LENGTH = 64

@Inject
class BeersLocalSourceImpl(
  private val db: BeersDatabase,
  private val epochTimeProvider: EpochTimeProvider = SystemEpochTimeProvider(),
) : BeersStorage {

  override fun observeBeers(): Flow<List<StoredBeer>> =
    db.beersDao().getAllBeers().map { beers -> beers.map { it.toStoredBeer() } }

  override suspend fun getBeerById(id: String): StoredBeer? =
    db.beersDao().getBeerById(id)?.toStoredBeer()

  override fun observeCatalogBeers(): Flow<List<StoredBeer>> =
    db.beersDao().getCatalogBeers().map { beers -> beers.map { it.toStoredBeer() } }

  override fun observeFavoriteBeers(): Flow<List<StoredBeer>> =
    db.beersDao().getFavoriteBeers().map { beers -> beers.map { it.toStoredBeer() } }

  override fun observeSavedFilterPresets(): Flow<List<StoredFilterPreset>> =
    db.beersDao().getFilterPresets().map { presets -> presets.map { it.toStoredFilterPreset() } }

  override suspend fun saveFilterPreset(preset: StoredFilterPreset) {
    validatePreset(preset)
    db.beersDao().saveFilterPreset(preset.toDbModel())
  }

  override suspend fun renameFilterPreset(id: String, name: String, updatedAt: Long) {
    require(id.isNotBlank()) { "Saved filter preset id must not be blank" }
    require(name.isNotBlank()) { "Saved filter preset name must not be blank" }
    require(name.length <= MAX_PRESET_NAME_LENGTH) {
      "Saved filter preset name must be at most $MAX_PRESET_NAME_LENGTH characters"
    }
    require(updatedAt >= 0L) { "Saved filter preset timestamp must not be negative" }
    db.beersDao().renameFilterPreset(id, name, updatedAt)
  }

  override suspend fun deleteFilterPreset(id: String) {
    require(id.isNotBlank()) { "Saved filter preset id must not be blank" }
    db.beersDao().deleteFilterPreset(id)
  }

  override suspend fun insertAll(beers: List<StoredBeer>) =
    db.beersDao().insertAll(beers.map { it.toDbModel() })

  override suspend fun insertPage(
    beers: List<StoredBeer>,
    surface: String,
    nextKey: Int?,
    totalCount: Int?,
    @Suppress("UNUSED_PARAMETER") pageKey: Int?,
  ) = db.beersDao().insertPage(
    beers.map { it.toDbModel() },
    surface,
    nextKey,
    totalCount,
    epochTimeProvider.epochMillis(),
  )

  override suspend fun getPagingState(surface: String): StoredPagingState? =
    db.beersDao().getPagingState(surface)?.toStoredPagingState()

  override suspend fun countPagingStates() = db.beersDao().countPagingStates()

  override suspend fun upsertAvailability(beer: StoredBeer) =
    db.beersDao().upsertAvailability(beer.toDbModel())

  override suspend fun upsertFavorite(beer: StoredBeer) =
    db.beersDao().upsertFavorite(beer.toDbModel())

  override suspend fun deleteAll() = db.beersDao().deleteAll()

  override suspend fun count() = db.beersDao().getCount()

  override suspend fun countCatalogEntries() = db.beersDao().getCatalogCount()

  private fun validatePreset(preset: StoredFilterPreset) {
    require(preset.id.isNotBlank()) { "Saved filter preset id must not be blank" }
    require(preset.name.isNotBlank()) { "Saved filter preset name must not be blank" }
    require(preset.name.length <= MAX_PRESET_NAME_LENGTH) {
      "Saved filter preset name must be at most $MAX_PRESET_NAME_LENGTH characters"
    }
    require(preset.updatedAt >= 0L) { "Saved filter preset timestamp must not be negative" }
  }

  private fun StoredFilterPreset.toDbModel() =
    SavedFilterPresetDbModel(id, name, search, styleId, breweryId, updatedAt)

  private fun SavedFilterPresetDbModel.toStoredFilterPreset() =
    StoredFilterPreset(id, name, search, styleId, breweryId, updatedAt)

  private fun StoredBeer.toDbModel() =
    BeerDbModel(
      id = id,
      name = name,
      tagline = tagline,
      description = description,
      imageUrl = imageUrl,
      abv = abv,
      ibu = ibu,
      foodPairing = Converters.listToJson(foodPairing),
      availability = availability,
      isFavorite = isFavorite,
      styleName = styleName,
      breweryName = breweryName,
      srm = srm,
      releasedYear = releasedYear,
      minServingTemperature = minServingTemperature,
      maxServingTemperature = maxServingTemperature,
      fermentationMethod = fermentationMethod,
      ingredients = Converters.listToJson(ingredients),
      recommendedGlasses = Converters.listToJson(recommendedGlasses),
    )

  private fun BeerDbModel.toStoredBeer() =
    StoredBeer(
      id = id,
      name = name,
      tagline = tagline,
      description = description,
      imageUrl = imageUrl,
      abv = abv,
      ibu = ibu,
      foodPairing = Converters.jsonToList(foodPairing),
      availability = availability,
      isFavorite = isFavorite,
      styleName = styleName,
      breweryName = breweryName,
      srm = srm,
      releasedYear = releasedYear,
      minServingTemperature = minServingTemperature,
      maxServingTemperature = maxServingTemperature,
      fermentationMethod = fermentationMethod,
      ingredients = Converters.jsonToList(ingredients),
      recommendedGlasses = Converters.jsonToList(recommendedGlasses),
    )

  private fun PagingStateDbModel.toStoredPagingState() =
    StoredPagingState(
      surface = surface,
      nextKey = nextKey,
      totalCount = totalCount,
      refreshedAt = refreshedAt,
    )
}
