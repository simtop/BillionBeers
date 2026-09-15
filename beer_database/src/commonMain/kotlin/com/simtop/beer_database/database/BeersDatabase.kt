package com.simtop.beer_database.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.simtop.beer_database.models.BeerDbModel
import com.simtop.beer_database.models.PagingStateDbModel

@Database(entities = [BeerDbModel::class, PagingStateDbModel::class], version = 4)
@ConstructedBy(BeersDatabaseConstructor::class)
abstract class BeersDatabase : RoomDatabase() {
  abstract fun beersDao(): BeersDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object BeersDatabaseConstructor : RoomDatabaseConstructor<BeersDatabase>
