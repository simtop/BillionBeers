package com.simtop.beer_network.di

import com.simtop.beer_network.network.BeersService
import com.simtop.beer_network.network.KtorBeersService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient

@ContributesTo(AppScope::class)
interface BeersNetworkModule {

  @Provides
  @SingleIn(AppScope::class)
  fun provideBeersApi(httpClient: HttpClient): BeersService = KtorBeersService(httpClient)
}
