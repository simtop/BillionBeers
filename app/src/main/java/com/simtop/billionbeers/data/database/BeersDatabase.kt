package com.simtop.billionbeers.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.simtop.billionbeers.data.models.BeerDbModel
import com.simtop.billionbeers.data.models.RemoteKeys

@Database(entities = [BeerDbModel::class, RemoteKeys::class], version = 1)
abstract class BeersDatabase : RoomDatabase() {
    abstract fun beersDao(): BeersDao
    abstract fun remoteKeysDao(): RemoteKeysDao
}