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

  @Test
  fun migrate3To4_keepsLocalDataAndDefaultsFavoriteToFalse() {
    val dbName = "migration-test-3-4"

    helper.createDatabase(dbName, 3).apply {
      execSQL(
        "INSERT INTO beers " +
          "(id, name, tagline, description, image_url, abv, ibu, food_pairing, availability, " +
          "style_name, brewery_name, srm, released_year, min_serving_temperature, " +
          "max_serving_temperature, fermentation_method, ingredients, recommended_glasses) " +
          "VALUES ('1', 'Beer 1', 'Tagline', 'Description', 'image', 5.0, 40.0, '[\\\"Food\\\"]', 0, " +
          "'IPA', 'Brewery', 8, 2024, 4, 8, 'Ale', '[\\\"Water\\\"]', '[\\\"Pint\\\"]')"
      )
      execSQL(
        "INSERT INTO paging_state (surface, next_key, total_count, refreshed_at) " +
          "VALUES ('catalog:en', 3, 206, 1234)"
      )
      close()
    }

    val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)
    assertEquals(4, db.version)

    db
      .query(
        "SELECT availability, name, style_name, brewery_name, is_favorite FROM beers WHERE id = '1'"
      )
      .use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals(0, cursor.getInt(0))
        assertEquals("Beer 1", cursor.getString(1))
        assertEquals("IPA", cursor.getString(2))
        assertEquals("Brewery", cursor.getString(3))
        assertEquals(0, cursor.getInt(4))
      }
    db.query("SELECT next_key, total_count, refreshed_at FROM paging_state").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(3, cursor.getInt(0))
      assertEquals(206, cursor.getInt(1))
      assertEquals(1234, cursor.getLong(2))
    }
    db.close()
  }

  @Test
  fun migrate1To4_preservesRowsLocalDataAndPagingStateAcrossFullChain() {
    val dbName = "migration-test-1-4"

    helper.createDatabase(dbName, 1).apply {
      execSQL(
        "INSERT INTO beers " +
          "(id, name, tagline, description, image_url, abv, ibu, food_pairing, availability) " +
          "VALUES ('1', 'Beer 1', '', '', '', 0.0, 0.0, '[]', 0)"
      )
      close()
    }

    val db =
      helper.runMigrationsAndValidate(
        dbName,
        4,
        true,
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
      )

    db
      .query("SELECT availability, style_name, ingredients, is_favorite FROM beers WHERE id = '1'")
      .use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals(0, cursor.getInt(0))
        assertEquals("", cursor.getString(1))
        assertEquals("[]", cursor.getString(2))
        assertEquals(0, cursor.getInt(3))
      }
    db.execSQL(
      "INSERT INTO paging_state (surface, next_key, total_count, refreshed_at) " +
        "VALUES ('catalog:en', 2, 206, 5678)"
    )
    db.query("SELECT next_key, total_count FROM paging_state WHERE surface = 'catalog:en'").use {
      cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(2, cursor.getInt(0))
      assertEquals(206, cursor.getInt(1))
    }
    db.close()
  }

  @Test
  fun migrate2To3_keepsBeersAndPagingState_andAddsDetailColumnsWithDefaults() {
    val dbName = "migration-test-2-3"

    // v2: seed one beer (locally edited availability) and a paging_state bookmark.
    helper.createDatabase(dbName, 2).apply {
      execSQL(
        "INSERT INTO beers " +
          "(id, name, tagline, description, image_url, abv, ibu, food_pairing, availability) " +
          "VALUES ('1', 'Beer 1', '', '', '', 0.0, 0.0, '[]', 0)"
      )
      execSQL(
        "INSERT INTO paging_state (surface, next_key, total_count, refreshed_at) " +
          "VALUES ('catalog:en', 3, 206, 0)"
      )
      close()
    }

    val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

    // The pre-existing beer and its edited availability survived...
    db
      .query(
        "SELECT availability, style_name, brewery_name, srm, ingredients, recommended_glasses " +
          "FROM beers WHERE id = '1'"
      )
      .use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals(0, cursor.getInt(0))
        // ...and the new columns carry their defaults, matching BeerDbModel's Kotlin defaults.
        assertEquals("", cursor.getString(1))
        assertEquals("", cursor.getString(2))
        assertTrue(cursor.isNull(3))
        assertEquals("[]", cursor.getString(4))
        assertEquals("[]", cursor.getString(5))
      }

    // paging_state (an unrelated table) is untouched by this migration.
    db.query("SELECT next_key FROM paging_state WHERE surface = 'catalog:en'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(3, cursor.getInt(0))
    }
    db.close()
  }
}
