package com.simtop.billionbeers.di

import com.simtop.billionbeers.core.CoroutineDispatcherProvider
import com.simtop.billionbeers.core.DefaultCoroutineDispatcherProvider
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
    fun providesDispatcherProvider(): CoroutineDispatcherProvider = DefaultCoroutineDispatcherProvider()

}