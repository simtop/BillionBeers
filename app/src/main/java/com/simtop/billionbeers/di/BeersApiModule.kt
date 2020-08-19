package com.simtop.billionbeers.di

import com.simtop.billionbeers.BuildConfig
import com.simtop.billionbeers.data.network.BeersApiFactory
import com.simtop.billionbeers.data.network.BeersService
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object BeersApiModule {

    @JvmStatic
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