package com.simtop.billionbeers.di


import android.content.Context
import androidx.room.Room
import com.simtop.billionbeers.core.BEERS_DB_NAME
import com.simtop.billionbeers.data.database.BeersDao
import com.simtop.billionbeers.data.database.BeersDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(ApplicationComponent::class)
object BeersDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext app: Context): BeersDatabase =
        Room.databaseBuilder(
            app,
            BeersDatabase::class.java,
            BEERS_DB_NAME
        ).build()

    @Provides
    @Singleton
    fun provideBeersDao(db: BeersDatabase) : BeersDao = db.beersDao()
}