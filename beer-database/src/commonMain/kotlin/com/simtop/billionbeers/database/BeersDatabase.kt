package com.simtop.billionbeers.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.simtop.billionbeers.database.models.BeerDbModel
import com.simtop.billionbeers.database.models.PagingStateDbModel
import com.simtop.billionbeers.database.models.SavedFilterPresetDbModel

@Database(
  entities = [BeerDbModel::class, PagingStateDbModel::class, SavedFilterPresetDbModel::class],
  version = 9,
  exportSchema = true,
)
@ConstructedBy(BeersDatabaseConstructor::class)
abstract class BeersDatabase : RoomDatabase() {
  abstract fun beersDao(): BeersDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object BeersDatabaseConstructor : RoomDatabaseConstructor<BeersDatabase> {
  override fun initialize(): BeersDatabase
}
