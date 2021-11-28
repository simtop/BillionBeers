package com.simtop.core.di


import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.DefaultCoroutineDispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoroutineDispatchersModule {

    @Provides
    @Singleton
    fun providesDispatcherProvider(): CoroutineDispatcherProvider =
        DefaultCoroutineDispatcherProvider()

}