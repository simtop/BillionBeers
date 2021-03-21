package com.simtop.beerdomain.di

import com.simtop.beerdomain.BuildConfig
import com.simtop.beerdomain.data.network.BeersApiFactory
import com.simtop.beerdomain.data.network.BeersService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import javax.inject.Singleton

@Module
@InstallIn(ApplicationComponent::class)
object BeersApiModule {

    @Provides
    @Singleton
    fun beersApi() = createBeersApi()

    private fun createBeersApi(): BeersService {
        val beersApiFactory = BeersApiFactory(
            enableHttpLogging = BuildConfig.DEBUG
        )
        return beersApiFactory.remoteBeersApi()
    }
}