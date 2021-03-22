package com.simtop.beer_network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(ApplicationComponent::class)
object BeersUrlModule {

    @Provides
    @Singleton
    @Named("baseUrl")
    fun provideBaseUrl(): String {
        return "https://api.punkapi.com/v2/"
    }
}