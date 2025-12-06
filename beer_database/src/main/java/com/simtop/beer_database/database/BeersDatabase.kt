package com.simtop.beer_database.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.simtop.beer_database.models.BeerDbModel

@Database(entities = [BeerDbModel::class], version = 1)
abstract class BeersDatabase : RoomDatabase() {
  abstract fun beersDao(): BeersDao
}
