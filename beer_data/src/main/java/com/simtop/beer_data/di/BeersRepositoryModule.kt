package com.simtop.beer_data.di

import com.simtop.beer_data.repositories.BeersRepositoryImpl
import com.simtop.beerdomain.domain.repositories.BeersRepository
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