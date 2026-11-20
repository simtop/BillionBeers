package com.simtop.billionbeers.database.di

import com.simtop.billionbeers.database.BeersDatabase
import com.simtop.billionbeers.database.localsources.BeersLocalSourceImpl
import com.simtop.billionbeers.storage.api.BeersStorage
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
