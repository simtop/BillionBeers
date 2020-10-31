package com.simtop.billionbeers.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simtop.billionbeers.data.models.BeerDbModel

@Dao
abstract class BeersDao {

    @Query("SELECT * FROM beers")
    abstract fun getAllBeers(): List<BeerDbModel>

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
    abstract fun updateBeer(
        primaryKey: Int,
        availability: Boolean
    )

    @Query("DELETE FROM beers")
    abstract fun deleteAll()

    @Query("SELECT COUNT(id) FROM beers")
    abstract fun getCount(): Int
}