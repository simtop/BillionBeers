package com.simtop.beerdomain.di

import com.simtop.beerdomain.data.network.BeersService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(ApplicationComponent::class)
object BeersNetworkModule {

    @Provides
    @Singleton
    fun provideBitCoinApi(retrofit: Retrofit): BeersService =
        retrofit.create(BeersService::class.java)
}