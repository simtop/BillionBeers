package com.simtop.billionbeers.di

import com.simtop.billionbeers.data.repository.BeersRepositoryImpl
import com.simtop.billionbeers.domain.repository.BeersRepository
import dagger.Binds
import dagger.Module

@Module
abstract class BeersRepositoryModule {
    @Binds
    abstract fun getBeersRepository(repository: BeersRepositoryImpl): BeersRepository
}