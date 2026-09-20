package com.simtop.beer_storage.browser

import com.simtop.beer_storage.api.BeersStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface BeersStorageBrowserModule {
  @Provides
  @SingleIn(AppScope::class)
  fun provideIndexedDbBeersStorage(): IndexedDbBeersStorage = IndexedDbBeersStorage()

  @Provides
  fun provideBeersStorage(storage: IndexedDbBeersStorage): BeersStorage = storage
}
