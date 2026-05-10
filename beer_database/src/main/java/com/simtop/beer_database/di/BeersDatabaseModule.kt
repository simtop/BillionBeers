package com.simtop.beer_database.di

import android.content.Context
import androidx.room.Room
import com.simtop.beer_database.database.BeersDao
import com.simtop.beer_database.database.BeersDatabase
import com.simtop.core.core.BEERS_DB_NAME
import dev.zacsweers.metro.AppScope
import com.simtop.core.di.ApplicationContext
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface BeersDatabaseModule {

  @Provides
  @SingleIn(AppScope::class)
  fun provideDatabase(@ApplicationContext app: Context): BeersDatabase =
    Room.databaseBuilder(app, BeersDatabase::class.java, BEERS_DB_NAME).build()

  @Provides
  @SingleIn(AppScope::class)
  fun provideBeersDao(db: BeersDatabase): BeersDao = db.beersDao()
}
