package com.simtop.billionbeers.di

import com.simtop.beerdomain.domain.usecases.UpdateAvailabilityUseCase
import com.simtop.core.core.CoroutineDispatcherProvider

interface DynamicDependencies {
  val useCase: UpdateAvailabilityUseCase

  val coroutineDispatcher: CoroutineDispatcherProvider
}
