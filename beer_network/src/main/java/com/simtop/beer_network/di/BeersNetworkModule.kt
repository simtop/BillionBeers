package com.simtop.beer_network.di

import com.simtop.beer_network.network.BeersService
import com.simtop.core.di.AppScope
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import retrofit2.Retrofit

@ContributesTo(AppScope::class)
interface BeersNetworkModule {

  @Provides
  @SingleIn(AppScope::class)
  fun provideBeersApi(retrofit: Retrofit): BeersService = retrofit.create(BeersService::class.java)
}
