package com.simtop.beer_database.di

import android.content.Context
import androidx.room.Room
import com.simtop.beer_database.database.BeersDao
import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_database.database.MIGRATION_1_2
import com.simtop.core.BuildConfig
import com.simtop.core.core.BEERS_DB_NAME
import com.simtop.core.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface BeersDatabaseModule {

  @Provides
  @SingleIn(AppScope::class)
  fun provideDatabase(@ApplicationContext app: Context): BeersDatabase =
    Room.databaseBuilder(app, BeersDatabase::class.java, BEERS_DB_NAME)
      // Real, additive v1 -> v2 migration keeps the cache (and local availability edits) across
      // release upgrades. The destructive fallback is a debug-only dev net for schema churn on a
      // throwaway DB - it must never run in release, where it would wipe user data. The debug flag
      // reuses :core's BuildConfig (already enabled there, precedent: NetworkingModule) instead of
      // turning buildConfig on in this library module.
      .addMigrations(MIGRATION_1_2)
      .apply { if (BuildConfig.DEBUG) fallbackToDestructiveMigration(dropAllTables = true) }
      .build()

  @Provides
  @SingleIn(AppScope::class)
  fun provideBeersDao(db: BeersDatabase): BeersDao = db.beersDao()
}
