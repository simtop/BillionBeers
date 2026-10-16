package com.simtop.beer_database.di

import com.simtop.beer_database.localsources.BeersLocalSourceImpl
import com.simtop.beer_storage.api.BeersStorage
import com.simtop.core.core.EpochTimeProvider
import com.simtop.core.core.SystemEpochTimeProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface BeersLocalSourceModule {

  @Provides fun provideEpochTimeProvider(): EpochTimeProvider = SystemEpochTimeProvider()

  @Provides
  fun provideBeersStorage(beersLocalSourceImpl: BeersLocalSourceImpl): BeersStorage =
    beersLocalSourceImpl
}
