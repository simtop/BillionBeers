package com.simtop.billionbeers.di

import com.simtop.billionbeers.data.repository.BeersRepositoryImpl
import com.simtop.billionbeers.domain.repository.BeersRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent

@Module
@InstallIn(ApplicationComponent::class)
abstract class BeersRepositoryModule {
    @Binds
    abstract fun getBeersRepository(repository: BeersRepositoryImpl): BeersRepository
}