package com.simtop.billionbeers.di

import com.simtop.beerdomain.domain.usecases.UpdateAvailabilityUseCase
import com.simtop.core.core.CoroutineDispatcherProvider

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DynamicDependencies {
    val useCase: UpdateAvailabilityUseCase

    val coroutineDispatcher: CoroutineDispatcherProvider


}
