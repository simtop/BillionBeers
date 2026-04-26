package com.simtop.beer_database.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simtop.beer_database.models.BeerDbModel
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BeersDao {

  @Query("SELECT * FROM beers") abstract fun getAllBeers(): Flow<List<BeerDbModel>>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insertIgnoringConflicts(beers: List<BeerDbModel>): List<Long>

  //Simple insertAll if we don't need pull to refresh
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    abstract suspend fun insertAll(beers: List<BeerDbModel>)

  //More complex update to handle pull to refresh doesn't affect to availability field
  //which doesn't come from the network
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
        food_pairing = :foodPairing
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
    foodPairing: String
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
          beer.foodPairing
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
  abstract suspend fun updateBeer(primaryKey: String, availability: Boolean)

  @Query("DELETE FROM beers") abstract fun deleteAll()

  @Query("SELECT COUNT(id) FROM beers") abstract suspend fun getCount(): Int
}
