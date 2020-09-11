package com.simtop.billionbeers.di

import androidx.room.Room
import com.simtop.billionbeers.data.database.BeersDao
import com.simtop.billionbeers.data.database.BeersDatabase
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object TestModule {

    @Provides
    @Singleton
    fun provideBeersDao(db: BeersDatabase) : BeersDao = db.beersDao()

    @Singleton
    @Provides
    fun provideDatabase(app: TestBaseApplication): BeersDatabase {
        return Room
            .inMemoryDatabaseBuilder(app, BeersDatabase::class.java)
            .fallbackToDestructiveMigration()
            .build()
    }
}
