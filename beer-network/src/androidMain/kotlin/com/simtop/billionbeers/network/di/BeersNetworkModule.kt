package com.simtop.billionbeers.network.di

import com.simtop.billionbeers.network.BeersService
import com.simtop.billionbeers.network.KtorBeersService
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
