package com.simtop.beerdomain.di


import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.DefaultCoroutineDispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import javax.inject.Singleton

@Module
@InstallIn(ApplicationComponent::class)
object CoroutineDispatchersModule {

    @Provides
    @Singleton
    fun providesDispatcherProvider(): CoroutineDispatcherProvider =
        DefaultCoroutineDispatcherProvider()

}