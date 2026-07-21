package com.simtop.beer_database.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.simtop.beer_database.models.BeerDbModel
import com.simtop.beer_database.models.PagingStateDbModel
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BeersDao {

  @Query("SELECT * FROM beers") abstract fun getAllBeers(): Flow<List<BeerDbModel>>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insertIgnoringConflicts(beers: List<BeerDbModel>): List<Long>

  // Simple insertAll if we don't need pull to refresh
  //    @Insert(onConflict = OnConflictStrategy.REPLACE)
  //    abstract suspend fun insertAll(beers: List<BeerDbModel>)

  // Pull-to-refresh update that deliberately omits the availability column: the server's
  // `available`
  // only seeds a row on first insert, and a user's later edit is authoritative and must survive.
  @Query(
    """
        UPDATE beers
        SET
        name = :name,
        tagline = :tagline,
        description = :description,
        image_url = :imageUrl,
        abv = :abv,
        ibu = :ibu,
        food_pairing = :foodPairing,
        style_name = :styleName,
        brewery_name = :breweryName,
        srm = :srm,
        released_year = :releasedYear,
        min_serving_temperature = :minServingTemperature,
        max_serving_temperature = :maxServingTemperature,
        fermentation_method = :fermentationMethod,
        ingredients = :ingredients,
        recommended_glasses = :recommendedGlasses
        WHERE id = :id
        """
  )
  abstract suspend fun updateBeerDetails(
    id: String,
    name: String,
    tagline: String,
    description: String,
    imageUrl: String,
    abv: Double,
    ibu: Double,
    foodPairing: String,
    styleName: String,
    breweryName: String,
    srm: Int?,
    releasedYear: Int?,
    minServingTemperature: Int?,
    maxServingTemperature: Int?,
    fermentationMethod: String,
    ingredients: String,
    recommendedGlasses: String,
  )

  @androidx.room.Transaction
  open suspend fun insertAll(beers: List<BeerDbModel>) {
    val results = insertIgnoringConflicts(beers)
    for (i in results.indices) {
      if (results[i] == -1L) {
        val beer = beers[i]
        updateBeerDetails(
          beer.id,
          beer.name,
          beer.tagline,
          beer.description,
          beer.imageUrl,
          beer.abv,
          beer.ibu,
          beer.foodPairing,
          beer.styleName,
          beer.breweryName,
          beer.srm,
          beer.releasedYear,
          beer.minServingTemperature,
          beer.maxServingTemperature,
          beer.fermentationMethod,
          beer.ingredients,
          beer.recommendedGlasses,
        )
      }
    }
  }

  @Query(
    """
        UPDATE beers
        SET
        availability = :availability
        WHERE id = :primaryKey
        """
  )
  abstract suspend fun updateBeer(primaryKey: String, availability: Boolean): Int

  /**
   * A user's availability edit. Usually the row is already cached and this is a plain column
   * update, but a beer reached through a query surface (search/browse) may not be in the table yet
   * - then the zero-row update falls back to inserting the full row, so the edit is persisted
   *   instead of silently matching nothing. Once the row exists, the keyed upsert's
   *   availability-preserving rule protects the edit from later catalog fetches like any other.
   */
  @androidx.room.Transaction
  open suspend fun upsertAvailability(beer: BeerDbModel) {
    if (updateBeer(beer.id, beer.availability) == 0) {
      insertIgnoringConflicts(listOf(beer))
    }
  }

  @Query("DELETE FROM beers") abstract suspend fun deleteAll()

  @Query("SELECT COUNT(id) FROM beers") abstract suspend fun getCount(): Int

  @Query("SELECT * FROM paging_state WHERE surface = :surface")
  abstract suspend fun getPagingState(surface: String): PagingStateDbModel?

  // Distinguishes a legacy pre-paging_state cache (rows, zero bookmarks) from a cache whose
  // bookmarks all belong to other surfaces (a language switch).
  @Query("SELECT COUNT(surface) FROM paging_state") abstract suspend fun countPagingStates(): Int

  @Upsert abstract suspend fun upsertPagingState(state: PagingStateDbModel)

  /**
   * Writes a fetched page and its paging bookmark atomically, so [PagingStateDbModel.nextKey] can
   * never point past — or short of — the rows actually stored (the PR #57 divergence class).
   * [nextKey] is merged monotonically: a refresh re-fetching page 1 (`nextKey = 2`) must not rewind
   * a warm cache that already reached a later page, so the stored value only ever advances. A null
   * incoming [nextKey] (last page) leaves the bookmark untouched — end-of-pagination is decided by
   * the fetch's total-count math, not by persisting a null key.
   */
  @androidx.room.Transaction
  open suspend fun insertPage(
    beers: List<BeerDbModel>,
    surface: String,
    nextKey: Int?,
    totalCount: Int?,
    refreshedAt: Long,
  ) {
    insertAll(beers)
    val existing = getPagingState(surface)
    upsertPagingState(
      PagingStateDbModel(
        surface = surface,
        nextKey = listOfNotNull(existing?.nextKey, nextKey).maxOrNull(),
        totalCount = totalCount ?: existing?.totalCount,
        refreshedAt = refreshedAt,
      )
    )
  }
}
