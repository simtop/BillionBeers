package com.simtop.core.di

import com.simtop.billionbeers.domain.repositories.BeerCatalogMetadataRepository
import com.simtop.billionbeers.domain.repositories.BeersPagerFactory
import com.simtop.billionbeers.domain.repositories.BeersRepository
import com.simtop.core.core.CoroutineDispatcherProvider

interface DynamicDependencies {
  val beersRepository: BeersRepository

  val beerCatalogMetadataRepository: BeerCatalogMetadataRepository

  val beersPagerFactory: BeersPagerFactory

  val coroutineDispatcher: CoroutineDispatcherProvider
}
