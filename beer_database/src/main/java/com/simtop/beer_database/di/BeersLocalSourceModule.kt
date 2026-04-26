package com.simtop.beer_database.di

import com.simtop.beer_database.localsources.BeersLocalSource
import com.simtop.beer_database.localsources.BeersLocalSourceImpl
import com.simtop.core.di.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface BeersLocalSourceModule {

  @Provides
  fun provideBeersLocalSource(beersLocalSourceImpl: BeersLocalSourceImpl): BeersLocalSource =
    beersLocalSourceImpl
}
