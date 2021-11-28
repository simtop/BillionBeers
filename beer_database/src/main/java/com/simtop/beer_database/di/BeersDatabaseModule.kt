package com.simtop.beer_database.di


import android.content.Context
import androidx.room.Room
import com.simtop.beer_database.database.BeersDao
import com.simtop.beer_database.database.BeersDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BeersDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext app: Context): BeersDatabase =
        Room.databaseBuilder(
            app,
            BeersDatabase::class.java,
            com.simtop.core.core.BEERS_DB_NAME
        ).build()

    @Provides
    @Singleton
    fun provideBeersDao(db: BeersDatabase) : BeersDao = db.beersDao()
}