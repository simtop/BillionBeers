package com.simtop.beer_network.di

import com.simtop.beer_network.network.BeersService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BeersNetworkModule {

    @Provides
    @Singleton
    fun provideBitCoinApi(retrofit: Retrofit): BeersService =
        retrofit.create(BeersService::class.java)
}