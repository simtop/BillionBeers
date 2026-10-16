package com.simtop.beer_database.di

import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_database.localsources.BeersLocalSourceImpl
import com.simtop.beer_storage.api.BeersStorage
import com.simtop.core.core.EpochTimeProvider
import com.simtop.core.core.SystemEpochTimeProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface BeersDatabaseModule {

  @Provides
  @SingleIn(AppScope::class)
  fun provideEpochTimeProvider(): EpochTimeProvider = SystemEpochTimeProvider()

  @Provides
  @SingleIn(AppScope::class)
  fun provideBeersStorage(
    database: BeersDatabase,
    epochTimeProvider: EpochTimeProvider,
  ): BeersStorage = BeersLocalSourceImpl(database, epochTimeProvider)
}
