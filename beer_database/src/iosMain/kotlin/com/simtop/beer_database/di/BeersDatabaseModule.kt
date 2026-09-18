package com.simtop.beer_database.di

import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_database.localsources.BeersLocalSourceImpl
import com.simtop.beer_storage.api.BeersStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface BeersDatabaseModule {

  @Provides
  @SingleIn(AppScope::class)
  fun provideBeersStorage(database: BeersDatabase): BeersStorage =
    BeersLocalSourceImpl(database)
}
