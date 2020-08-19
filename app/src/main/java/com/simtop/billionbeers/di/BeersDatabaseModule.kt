package com.simtop.billionbeers.di

import android.content.Context
import androidx.room.Room
import com.simtop.billionbeers.core.BEERS_DB_NAME
import com.simtop.billionbeers.data.database.BeersDao
import com.simtop.billionbeers.data.database.BeersDatabase
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class BeersDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(context: Context): BeersDatabase =
        Room.databaseBuilder(
            context,
            BeersDatabase::class.java,
            BEERS_DB_NAME
        ).build()

    @Provides
    @Singleton
    fun provideBeersDao(db: BeersDatabase) : BeersDao = db.beersDao()
}