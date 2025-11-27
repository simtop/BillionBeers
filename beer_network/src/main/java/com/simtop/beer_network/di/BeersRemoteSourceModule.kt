package com.simtop.beer_network.di

import com.simtop.beer_network.remotesources.BeersRemoteSource
import com.simtop.beer_network.remotesources.BeersRemoteSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BeersRemoteSourceModule {

    @Binds
    abstract fun bindBeersRemoteSource(
        beersRemoteSourceImpl: BeersRemoteSourceImpl
    ): BeersRemoteSource
}
