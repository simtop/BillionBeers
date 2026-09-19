package com.simtop.beer_storage.browser

import com.simtop.beer_storage.api.BeersStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface BeersStorageBrowserModule {
  @Provides
  fun provideBeersStorage(): BeersStorage = IndexedDbBeersStorage()
}
