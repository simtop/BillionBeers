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

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertAll(beers: List<BeerDbModel>)

  @Query(
    """
        UPDATE beers 
        SET 
        availability = :availability
        WHERE id = :primaryKey
        """
  )
  abstract fun updateBeer(primaryKey: String, availability: Boolean)

  @Query("DELETE FROM beers") abstract fun deleteAll()

  @Query("SELECT COUNT(id) FROM beers") abstract suspend fun getCount(): Int
}
