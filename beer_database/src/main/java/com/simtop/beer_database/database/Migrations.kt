package com.simtop.beer_database.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: the project's first migration. Purely additive — it creates the `paging_state`
 * bookmark table and leaves `beers` (and the user-owned availability column) untouched, so an
 * in-place upgrade preserves the cache. The `CREATE TABLE` statement mirrors Room's generated
 * schema for [com.simtop.beer_database.models.PagingStateDbModel] exactly; the migration test
 * (schemas are exported) guards against drift.
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
