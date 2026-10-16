package com.simtop.beer_data.di

import com.simtop.beer_data.repositories.BeersPagerFactoryImpl
import com.simtop.beer_data.repositories.BeersRepositoryImpl
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.domain.repositories.BeerCatalogMetadataRepository
import com.simtop.beerdomain.domain.repositories.SavedFilterPresetsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface BeersRepositoryModule {
  @Provides
  fun provideBeersRepository(repository: BeersRepositoryImpl): BeersRepository = repository

  @Provides
  fun provideBeerCatalogMetadataRepository(
    repository: BeersRepositoryImpl
  ): BeerCatalogMetadataRepository = repository

  @Provides
  fun provideSavedFilterPresetsRepository(
    repository: BeersRepositoryImpl
  ): SavedFilterPresetsRepository = repository

  @Provides
  fun provideBeersPagerFactory(factory: BeersPagerFactoryImpl): BeersPagerFactory = factory
}
