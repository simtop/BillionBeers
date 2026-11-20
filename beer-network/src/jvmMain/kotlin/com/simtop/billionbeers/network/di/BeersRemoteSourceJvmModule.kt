package com.simtop.billionbeers.network.di

import com.simtop.billionbeers.network.remotesources.BeersRemoteSource
import com.simtop.billionbeers.network.remotesources.BeersRemoteSourceImpl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface BeersRemoteSourceJvmModule {

  @Provides
  fun provideBeersRemoteSource(implementation: BeersRemoteSourceImpl): BeersRemoteSource =
    implementation
}
