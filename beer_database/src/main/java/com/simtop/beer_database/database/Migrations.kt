package com.simtop.beer_database.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: the project's first migration. Purely additive — it creates the `paging_state` bookmark
 * table and leaves `beers` (and the user-owned availability column) untouched, so an in-place
 * upgrade preserves the cache. The `CREATE TABLE` statement mirrors Room's generated schema for
 * [com.simtop.beer_database.models.PagingStateDbModel] exactly; the migration test (schemas are
 * exported) guards against drift.
 */
val MIGRATION_1_2 =
  object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `paging_state` (" +
          "`surface` TEXT NOT NULL, " +
          "`next_key` INTEGER, " +
          "`total_count` INTEGER, " +
          "`refreshed_at` INTEGER NOT NULL, " +
          "PRIMARY KEY(`surface`))"
      )
    }
  }

/**
 * v2 → v3: additive detail columns on `beers` (Paging 2.0 Phase 4 §6.5), defaults matching
 * [com.simtop.beer_database.models.BeerDbModel]'s `defaultValue`s exactly. Existing rows -
 * including the user-owned availability - are untouched; migrated rows carry the empty defaults
 * until the next refresh's upsert fills the fields from the embedded API objects.
 */
val MIGRATION_2_3 =
  object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE `beers` ADD COLUMN `style_name` TEXT NOT NULL DEFAULT ''")
      db.execSQL("ALTER TABLE `beers` ADD COLUMN `brewery_name` TEXT NOT NULL DEFAULT ''")
      db.execSQL("ALTER TABLE `beers` ADD COLUMN `srm` INTEGER")
      db.execSQL("ALTER TABLE `beers` ADD COLUMN `released_year` INTEGER")
      db.execSQL("ALTER TABLE `beers` ADD COLUMN `min_serving_temperature` INTEGER")
      db.execSQL("ALTER TABLE `beers` ADD COLUMN `max_serving_temperature` INTEGER")
      db.execSQL("ALTER TABLE `beers` ADD COLUMN `fermentation_method` TEXT NOT NULL DEFAULT ''")
      db.execSQL("ALTER TABLE `beers` ADD COLUMN `ingredients` TEXT NOT NULL DEFAULT '[]'")
      db.execSQL("ALTER TABLE `beers` ADD COLUMN `recommended_glasses` TEXT NOT NULL DEFAULT '[]'")
    }
  }
