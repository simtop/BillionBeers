package com.simtop.billionbeers.iosshared

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_database.database.MIGRATION_1_2
import com.simtop.beer_database.database.MIGRATION_2_3
import com.simtop.beer_database.database.MIGRATION_3_4
import com.simtop.core.core.EnvironmentConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.Json
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory

private const val DATABASE_DIRECTORY = "/Library/Application Support/BillionBeers"
private const val DATABASE_NAME = "beers_database.db"

internal actual fun createBridgeHttpClient(environment: EnvironmentConfig): HttpClient =
  HttpClient(Darwin) {
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

@OptIn(ExperimentalForeignApi::class)
internal actual fun createBridgeDatabase(): BeersDatabase {
  val directory = NSHomeDirectory() + DATABASE_DIRECTORY
  NSFileManager.defaultManager.createDirectoryAtPath(
    path = directory,
    withIntermediateDirectories = true,
    attributes = null,
    error = null,
  )
  return Room.databaseBuilder<BeersDatabase>("$directory/$DATABASE_NAME")
    .setDriver(BundledSQLiteDriver())
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    .build()
}
