package com.simtop.billionbeers.di

import com.simtop.beer_network.network.configureBeersApi
import com.simtop.core.core.EnvironmentConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient

/** Wires shared API defaults to Android's cache- and debug-policy-aware OkHttp engine. */
@ContributesTo(AppScope::class)
interface AndroidHttpClientModule {

  @Provides
  @SingleIn(AppScope::class)
  fun provideHttpClient(environment: EnvironmentConfig, okHttpClient: OkHttpClient): HttpClient =
    HttpClient(OkHttp) {
      engine { preconfigured = okHttpClient }
      configureBeersApi(environment)
    }
}
