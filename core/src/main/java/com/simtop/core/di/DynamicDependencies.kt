package com.simtop.core.di

import com.simtop.beerdomain.domain.repositories.BeerCatalogMetadataRepository
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.CoroutineDispatcherProvider

interface DynamicDependencies {
  val beersRepository: BeersRepository

  val beerCatalogMetadataRepository: BeerCatalogMetadataRepository

  val beersPagerFactory: BeersPagerFactory

  val coroutineDispatcher: CoroutineDispatcherProvider
}
