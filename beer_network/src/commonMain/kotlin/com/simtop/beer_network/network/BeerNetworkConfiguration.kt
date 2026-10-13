package com.simtop.beer_network.network

import com.simtop.core.core.EnvironmentConfig
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Shared lenient API decoder used by every host and network test. */
val BeersApiJson: Json =
  Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
  }

/** Applies the API defaults while leaving the engine and its platform policy to each host. */
fun HttpClientConfig<*>.configureBeersApi(environment: EnvironmentConfig) {
  expectSuccess = false
  defaultRequest { url(environment.apiBaseUrl) }
  install(ContentNegotiation) { json(BeersApiJson) }
  install(HttpTimeout) {
    connectTimeoutMillis = environment.connectTimeout.inWholeMilliseconds
    socketTimeoutMillis = environment.readTimeout.inWholeMilliseconds
    requestTimeoutMillis = environment.requestTimeout.inWholeMilliseconds
  }
}
