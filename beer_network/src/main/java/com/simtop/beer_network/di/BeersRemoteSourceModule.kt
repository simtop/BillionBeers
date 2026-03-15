package com.simtop.beer_network.di

import com.simtop.beer_network.remotesources.BeersRemoteSource
import com.simtop.beer_network.remotesources.BeersRemoteSourceImpl
import com.simtop.core.di.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface BeersRemoteSourceModule {

  @Provides
  fun provideBeersRemoteSource(
    beersRemoteSourceImpl: BeersRemoteSourceImpl
  ): BeersRemoteSource = beersRemoteSourceImpl
}
