package com.simtop.beer_network.di

import com.simtop.beer_network.network.BeersService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object BeersNetworkModule {

  @Provides
  @Singleton
  fun provideBeersApi(retrofit: Retrofit): BeersService = retrofit.create(BeersService::class.java)
}
