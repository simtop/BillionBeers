package com.simtop.beer_database.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.simtop.beer_database.models.BeerDbModel
import com.simtop.beer_database.models.PagingStateDbModel

@Database(entities = [BeerDbModel::class, PagingStateDbModel::class], version = 3)
abstract class BeersDatabase : RoomDatabase() {
  abstract fun beersDao(): BeersDao
}
