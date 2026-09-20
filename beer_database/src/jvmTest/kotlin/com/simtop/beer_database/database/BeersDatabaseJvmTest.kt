package com.simtop.beer_database.database

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.simtop.beer_database.models.BeerDbModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

private fun SQLiteConnection.execute(sql: String) {
  prepare(sql).use { it.step() }
}

class BeersDatabaseJvmTest {

  private lateinit var db: BeersDatabase
  private val databaseDirectory = createTempDirectory("beer-database-test").toFile()

  @Before
  fun setUp() {
    db = newInMemoryDatabase()
  }

  @After
  fun tearDown() {
    db.close()
    databaseDirectory.deleteRecursively()
  }

  @Test
  fun insertPageCommitsRowsAndBookmarkTogether() = runBlocking {
    db.beersDao().insertPage(listOf(beer("1")), "catalog:en", 2, 206, 10L)

    assertEquals(1, db.beersDao().getCount())
    assertEquals(2, db.beersDao().getPagingState("catalog:en")?.nextKey)
    assertEquals(206, db.beersDao().getPagingState("catalog:en")?.totalCount)
  }

  @Test
  fun insertPageMergesBookmarkMonotonicallyAndKeepsExistingTotal() = runBlocking {
    val dao = db.beersDao()
    dao.insertPage(listOf(beer("1")), "catalog:en", 5, 206, 10L)
    dao.insertPage(listOf(beer("1")), "catalog:en", 2, null, 20L)

    assertEquals(5, dao.getPagingState("catalog:en")?.nextKey)
    assertEquals(206, dao.getPagingState("catalog:en")?.totalCount)
    assertEquals(20L, dao.getPagingState("catalog:en")?.refreshedAt)
  }

  @Test
  fun localFlagsSurviveCatalogRefreshAndFlowsExposeCommittedFavorites() = runBlocking {
    val dao = db.beersDao()
    dao.insertAll(listOf(beer("1", availability = false, isFavorite = true)))
    dao.insertAll(listOf(beer("1", name = "Refreshed", availability = true, isFavorite = false)))

    val stored = dao.getAllBeers().first().single()
    assertEquals("Refreshed", stored.name)
    assertFalse(stored.availability)
    assertTrue(stored.isFavorite)
    assertEquals(listOf("Refreshed"), dao.getFavoriteBeers().first().map(BeerDbModel::name))
  }

  @Test
  fun insertAllWithNoRowsIsANoOp() = runBlocking {
    val dao = db.beersDao()

    dao.insertAll(emptyList())

    assertEquals(0, dao.getCount())
    assertEquals(emptyList<BeerDbModel>(), dao.getAllBeers().first())
  }

  @Test
  fun insertAllUpdatesAConflictAtItsReturnedIndex() = runBlocking {
    val dao = db.beersDao()
    val existing = beer("1", availability = false, isFavorite = true)
    val newBeer =
      beer("2", name = "New Catalog").copy(
        tagline = "New tagline",
        description = "New description",
        imageUrl = "new-image",
        abv = 6.2,
        ibu = 41.0,
        foodPairing = "[\"new food\"]",
        styleName = "New style",
        breweryName = "New brewery",
        srm = 8,
        releasedYear = 2024,
        minServingTemperature = 4,
        maxServingTemperature = 8,
        fermentationMethod = "lagering",
        ingredients = "[\"malt\"]",
        recommendedGlasses = "[\"pint\"]",
      )
    val refreshedExisting =
      existing.copy(
        name = "Refreshed Catalog",
        tagline = "Refreshed tagline",
        description = "Refreshed description",
        imageUrl = "refreshed-image",
        abv = 7.1,
        ibu = 52.0,
        foodPairing = "[\"refreshed food\"]",
        availability = true,
        isFavorite = false,
        styleName = "Refreshed style",
        breweryName = "Refreshed brewery",
        srm = 12,
        releasedYear = 2025,
        minServingTemperature = 5,
        maxServingTemperature = 9,
        fermentationMethod = "ale",
        ingredients = "[\"hops\"]",
        recommendedGlasses = "[\"snifter\"]",
      )

    dao.insertAll(listOf(existing))
    dao.insertAll(listOf(newBeer, refreshedExisting))

    assertEquals(
      listOf(refreshedExisting.copy(availability = false, isFavorite = true), newBeer),
      dao.getAllBeers().first().sortedBy(BeerDbModel::id),
    )
  }

  @Test
  fun upsertAvailabilityInsertsACompleteRowWhenUncached() = runBlocking {
    val dao = db.beersDao()
    val uncached =
      beer("42", name = "Uncached", availability = false, isFavorite = true).copy(
        tagline = "Uncached tagline",
        description = "Uncached description",
        imageUrl = "uncached-image",
        abv = 5.7,
        ibu = 33.0,
        foodPairing = "[\"uncached food\"]",
        styleName = "Uncached style",
        breweryName = "Uncached brewery",
        srm = 6,
        releasedYear = 2023,
        minServingTemperature = 3,
        maxServingTemperature = 7,
        fermentationMethod = "fermentation",
        ingredients = "[\"grain\"]",
        recommendedGlasses = "[\"tulip\"]",
      )

    dao.upsertAvailability(uncached)

    assertEquals(uncached, dao.getAllBeers().first().single())
  }

  @Test
  fun upsertAvailabilityUpdatesACachedRowWithoutAddingRows() = runBlocking {
    val dao = db.beersDao()
    val cached = beer("cached", availability = true, isFavorite = true)
    val updated = cached.copy(availability = false)
    dao.insertAll(listOf(cached))

    dao.upsertAvailability(updated)

    assertEquals(1, dao.getCount())
    assertEquals(updated, dao.getAllBeers().first().single())
  }

  @Test
  fun insertPageRollsBackRowsWhenBookmarkWriteFails() = runBlocking {
    db.useConnection(false) { connection ->
      connection.usePrepared(
        "CREATE TRIGGER fail_paging_state_insert " +
          "BEFORE INSERT ON paging_state BEGIN SELECT RAISE(ABORT, 'bookmark failure'); END",
      ) { statement ->
        statement.step()
      }
    }

    try {
      db.beersDao().insertPage(listOf(beer("rollback")), "catalog:en", 2, 206, 10L)
      fail("insertPage should roll back when the bookmark write fails")
    } catch (_: RuntimeException) {
      assertEquals(0, db.beersDao().getCount())
      assertEquals(null, db.beersDao().getPagingState("catalog:en"))
    }
  }

  @Test
  fun historicalVersionOneFileMigratesThroughVersionFour() = runBlocking {
    val file = File(databaseDirectory, "version-one.db").absolutePath
    createVersionOneDatabase(file)
    db.close()
    db = newFileDatabase(file)

    val migrated = db.beersDao().getAllBeers().first().single()
    assertEquals("1", migrated.id)
    assertFalse(migrated.availability)
    assertEquals("", migrated.styleName)
    assertEquals("[]", migrated.ingredients)
    assertFalse(migrated.isFavorite)
  }

  @Test
  fun localFlagsPersistWhenAFileDatabaseIsReopened() = runBlocking {
    val file = File(databaseDirectory, "beers.db").absolutePath
    db.close()
    db = newFileDatabase(file)
    db.beersDao().upsertFavorite(beer("1", isFavorite = true))
    db.close()

    db = newFileDatabase(file)
    assertEquals(true, db.beersDao().getAllBeers().first().single().isFavorite)
  }

  private fun createVersionOneDatabase(file: String) {
    BundledSQLiteDriver().open(file).use { connection ->
      connection.execute("PRAGMA user_version = 1")
      connection.execute(
        "CREATE TABLE IF NOT EXISTS `beers` (" +
          "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `tagline` TEXT NOT NULL, " +
          "`description` TEXT NOT NULL, `image_url` TEXT NOT NULL, `abv` REAL NOT NULL, " +
          "`ibu` REAL NOT NULL, `food_pairing` TEXT NOT NULL, `availability` INTEGER NOT NULL, " +
          "PRIMARY KEY(`id`))"
      )
      connection.execute(
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"
      )
      connection.execute(
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, " +
          "'6e26c8319f69476d204f3ff558ed2338')"
      )
      connection.execute(
        "INSERT INTO beers " +
          "(id, name, tagline, description, image_url, abv, ibu, food_pairing, availability) " +
          "VALUES ('1', 'Legacy', '', '', '', 0.0, 0.0, '[]', 0)"
      )
    }
  }

  private fun newInMemoryDatabase(): BeersDatabase =
    Room.inMemoryDatabaseBuilder { BeersDatabaseConstructor.initialize() }
      .setDriver(BundledSQLiteDriver())
      .build()

  private fun newFileDatabase(file: String): BeersDatabase =
    Room.databaseBuilder(file) { BeersDatabaseConstructor.initialize() }
      .setDriver(BundledSQLiteDriver())
      .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
      .build()

  private fun beer(
    id: String,
    name: String = "Beer $id",
    availability: Boolean = true,
    isFavorite: Boolean = false,
  ) =
    BeerDbModel(
      id = id,
      name = name,
      tagline = "",
      description = "",
      imageUrl = "",
      abv = 0.0,
      ibu = 0.0,
      foodPairing = "[]",
      availability = availability,
      isFavorite = isFavorite,
    )
}
