package com.simtop.beer_database.di

import com.simtop.beer_database.localsources.BeersLocalSourceImpl
import com.simtop.beer_storage.api.BeersStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface BeersLocalSourceModule {

  @Provides
  fun provideBeersStorage(beersLocalSourceImpl: BeersLocalSourceImpl): BeersStorage =
    beersLocalSourceImpl
}
