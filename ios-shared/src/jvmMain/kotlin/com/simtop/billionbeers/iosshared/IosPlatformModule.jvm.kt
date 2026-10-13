package com.simtop.billionbeers.iosshared

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.simtop.beer_database.database.ALL_MIGRATIONS
import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_database.database.BeersDatabaseConstructor
import com.simtop.beer_network.network.configureBeersApi
import com.simtop.core.core.EnvironmentConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun createBridgeHttpClient(environment: EnvironmentConfig): HttpClient =
  HttpClient(OkHttp) { configureBeersApi(environment) }

internal actual fun createBridgeDatabase(): BeersDatabase =
  Room.databaseBuilder(
    "${System.getProperty("java.io.tmpdir")}billionbeers-ios-shared-jvm.db"
  ) { BeersDatabaseConstructor.initialize() }
    .setDriver(BundledSQLiteDriver())
    .addMigrations(*ALL_MIGRATIONS.toTypedArray())
    .build()
