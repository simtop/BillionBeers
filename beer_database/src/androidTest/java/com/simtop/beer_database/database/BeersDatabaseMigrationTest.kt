package com.simtop.beer_database.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the project's first migration: [MIGRATION_1_2] must add `paging_state` while leaving the
 * cached `beers` (and the user-owned `availability` column) intact. Schema validation against the
 * exported `2.json` also catches any drift between the migration SQL and the entity definition.
 */
@RunWith(AndroidJUnit4::class)
class BeersDatabaseMigrationTest {

  @get:Rule
  val helper =
    MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BeersDatabase::class.java)

  @Test
  fun migrate1To2_keepsBeers_andAddsPagingState() {
    val dbName = "migration-test"

    // v1: seed one beer whose availability was locally edited to false.
    helper.createDatabase(dbName, 1).apply {
      execSQL(
        "INSERT INTO beers " +
          "(id, name, tagline, description, image_url, abv, ibu, food_pairing, availability) " +
          "VALUES ('1', 'Beer 1', '', '', '', 0.0, 0.0, '[]', 0)"
      )
      close()
    }

    // Run the real migration; `true` validates the resulting schema matches the exported 2.json.
    val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

    // The cached beer (and its edited availability) survived the upgrade.
    db.query("SELECT id, availability FROM beers").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("1", cursor.getString(0))
      assertEquals(0, cursor.getInt(1))
    }

    // The new paging_state table exists and accepts a bookmark row.
    db.execSQL(
      "INSERT INTO paging_state (surface, next_key, total_count, refreshed_at) " +
        "VALUES ('catalog:en', 3, 206, 0)"
    )
    db.query("SELECT next_key, total_count FROM paging_state WHERE surface = 'catalog:en'").use {
      cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(3, cursor.getInt(0))
      assertEquals(206, cursor.getInt(1))
    }
    db.close()
  }
}
