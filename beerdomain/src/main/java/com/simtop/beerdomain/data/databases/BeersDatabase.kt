package com.simtop.beerdomain.data.databases

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [com.simtop.beerdomain.data.models.BeerDbModel::class], version = 1)
abstract class BeersDatabase : RoomDatabase() {
    abstract fun beersDao(): BeersDao
}