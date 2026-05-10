package com.simtop.beer_data.di

import com.simtop.beer_data.repositories.BeersRepositoryImpl
import com.simtop.beerdomain.domain.repositories.BeersRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface BeersRepositoryModule {
  @Provides
  fun provideBeersRepository(repository: BeersRepositoryImpl): BeersRepository = repository
}
