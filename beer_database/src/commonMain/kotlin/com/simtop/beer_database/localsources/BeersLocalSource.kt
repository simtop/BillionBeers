package com.simtop.beer_database.localsources

import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_database.models.BeerDbModel
import com.simtop.beer_database.models.PagingStateDbModel
import com.simtop.beer_database.utils.Converters
import com.simtop.beer_database.utils.currentTimeMillis
import com.simtop.beer_storage.api.BeersStorage
import com.simtop.beer_storage.api.StoredBeer
import com.simtop.beer_storage.api.StoredPagingState
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Inject
class BeersLocalSourceImpl(private val db: BeersDatabase) : BeersStorage {

  override fun observeBeers(): Flow<List<StoredBeer>> =
    db.beersDao().getAllBeers().map { beers -> beers.map { it.toStoredBeer() } }

  override fun observeFavoriteBeers(): Flow<List<StoredBeer>> =
    db.beersDao().getFavoriteBeers().map { beers -> beers.map { it.toStoredBeer() } }

  override suspend fun insertAll(beers: List<StoredBeer>) =
    db.beersDao().insertAll(beers.map { it.toDbModel() })

  override suspend fun insertPage(
    beers: List<StoredBeer>,
    surface: String,
    nextKey: Int?,
    totalCount: Int?,
  ) = db.beersDao().insertPage(
    beers.map { it.toDbModel() },
    surface,
    nextKey,
    totalCount,
    currentTimeMillis(),
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
