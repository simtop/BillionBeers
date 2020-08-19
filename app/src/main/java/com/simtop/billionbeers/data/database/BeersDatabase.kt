package com.simtop.billionbeers.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.simtop.billionbeers.data.models.BeerDbModel

@Database(entities = [BeerDbModel::class], version = 1)
abstract class BeersDatabase : RoomDatabase() {
    abstract fun beersDao(): BeersDao
}