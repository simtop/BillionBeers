package com.simtop.billionbeers.iosshared

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.simtop.beer_database.database.ALL_MIGRATIONS
import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_network.network.configureBeersApi
import com.simtop.core.core.EnvironmentConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory

private const val DATABASE_DIRECTORY = "/Library/Application Support/BillionBeers"
private const val DATABASE_NAME = "beers_database.db"

internal actual fun createBridgeHttpClient(environment: EnvironmentConfig): HttpClient =
  HttpClient(Darwin) { configureBeersApi(environment) }

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
    .addMigrations(*ALL_MIGRATIONS.toTypedArray())
    .build()
}
