package com.simtop.billionbeers.network.di

import com.simtop.billionbeers.network.BeersService
import com.simtop.billionbeers.network.createKtorBeersService
import com.simtop.billionbeers.network.remotesources.BeersRemoteSource
import com.simtop.billionbeers.network.remotesources.BeersRemoteSourceImpl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient

@ContributesTo(AppScope::class)
interface BeersNetworkWasmModule {

  @Provides
  @SingleIn(AppScope::class)
  fun provideBeersService(httpClient: HttpClient): BeersService = createKtorBeersService(httpClient)

  @Provides
  fun provideBeersRemoteSource(beersRemoteSourceImpl: BeersRemoteSourceImpl): BeersRemoteSource =
    beersRemoteSourceImpl
}
