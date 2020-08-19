package com.simtop.billionbeers.data.database

import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simtop.billionbeers.data.models.BeerDbModel

@Dao
abstract class BeersDao {

    @Query("SELECT * FROM beers")
    abstract fun getAllBeers(): DataSource.Factory<Int, BeerDbModel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(news: List<BeerDbModel>)

    @Query("""
        UPDATE beers 
        SET 
        availability = :availability
        WHERE id = :primaryKey
        """)
    abstract suspend fun updateBeer(
        primaryKey: String,
        availability: Boolean
    )

    @Query("DELETE FROM beers")
    abstract suspend fun deleteAll()
}