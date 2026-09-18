package com.simtop.billionbeers.iosshared

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_database.database.BeersDatabaseConstructor
import com.simtop.beer_database.database.MIGRATION_1_2
import com.simtop.beer_database.database.MIGRATION_2_3
import com.simtop.beer_database.database.MIGRATION_3_4
import com.simtop.core.core.EnvironmentConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal actual fun createBridgeHttpClient(environment: EnvironmentConfig): HttpClient =
  HttpClient(OkHttp) {
    expectSuccess = false
    defaultRequest { url(environment.apiBaseUrl) }
    install(ContentNegotiation) {
      json(
        Json {
          ignoreUnknownKeys = true
          coerceInputValues = true
          isLenient = true
        }
      )
    }
  }

internal actual fun createBridgeDatabase(): BeersDatabase =
  Room.databaseBuilder(
    "${System.getProperty("java.io.tmpdir")}billionbeers-ios-shared-jvm.db"
  ) { BeersDatabaseConstructor.initialize() }
    .setDriver(BundledSQLiteDriver())
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    .build()
