package com.simtop.beer_database.di

import com.simtop.beer_database.localsources.BeersLocalSource
import com.simtop.beer_database.localsources.BeersLocalSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BeersLocalSourceModule {

  @Binds
  abstract fun bindBeersLocalSource(beersLocalSourceImpl: BeersLocalSourceImpl): BeersLocalSource
}
