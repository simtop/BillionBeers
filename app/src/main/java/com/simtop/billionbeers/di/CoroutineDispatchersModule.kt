package com.simtop.billionbeers.di

import com.simtop.billionbeers.core.CoroutineDispatcherProvider
import com.simtop.billionbeers.core.DefaultCoroutineDispatcherProvider
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object CoroutineDispatchersModule {

    @Provides
    @Singleton
    fun providesDispatcherProvider(): CoroutineDispatcherProvider = DefaultCoroutineDispatcherProvider()

}