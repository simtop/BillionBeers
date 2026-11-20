package com.simtop.billionbeers.database.di

import com.simtop.billionbeers.database.localsources.BeersLocalSourceImpl
import com.simtop.billionbeers.storage.api.BeersStorage
import com.simtop.core.core.EpochTimeProvider
import com.simtop.core.core.SystemEpochTimeProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface BeersLocalSourceJvmModule {

  @Provides fun provideEpochTimeProvider(): EpochTimeProvider = SystemEpochTimeProvider()

  @Provides
  fun provideBeersStorage(implementation: BeersLocalSourceImpl): BeersStorage = implementation
}
