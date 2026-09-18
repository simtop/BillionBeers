package com.simtop.beer_database.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.simtop.beer_database.models.BeerDbModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory

@OptIn(ExperimentalForeignApi::class)
class BeersDatabaseIosTest {

  @Test
  fun databasePersistsRowsFlagsAndFlowAcrossCloseAndReopen() = runTest {
    val path = NSTemporaryDirectory() + "billionbeers-native-test.db"
    val fileManager = NSFileManager.defaultManager
    fileManager.removeItemAtPath(path, error = null)

    val first = open(path)
    val beer = BeerDbModel(
      id = "native-1",
      name = "Native Lager",
      tagline = "A test beer",
      description = "A native Room test row",
      imageUrl = "",
      abv = 4.0,
      ibu = 20.0,
      foodPairing = "[]",
    )

    first.beersDao().insertPage(
      beers = listOf(beer),
      surface = "catalog",
      nextKey = 2,
      totalCount = 1,
      refreshedAt = 1L,
    )
    first.beersDao().upsertFavorite(beer.copy(isFavorite = true))
    first.beersDao().upsertAvailability(beer.copy(availability = false))

    assertEquals(false, first.beersDao().getAllBeers().first().single().availability)
    assertTrue(first.beersDao().getFavoriteBeers().first().single().isFavorite)
    assertEquals(2, first.beersDao().getPagingState("catalog")?.nextKey)
    first.close()

    val reopened = open(path)
    val persisted = reopened.beersDao().getAllBeers().first().single()
    assertEquals("native-1", persisted.id)
    assertFalse(persisted.availability)
    assertTrue(persisted.isFavorite)
    assertEquals(2, reopened.beersDao().getPagingState("catalog")?.nextKey)
    reopened.close()

    fileManager.removeItemAtPath(path, error = null)
  }

  private fun open(path: String): BeersDatabase =
    Room.databaseBuilder<BeersDatabase>(path)
      .setDriver(BundledSQLiteDriver())
      .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
      .build()
}
