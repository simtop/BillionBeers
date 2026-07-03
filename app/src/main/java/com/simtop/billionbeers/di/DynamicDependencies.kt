package com.simtop.billionbeers.di

import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.CoroutineDispatcherProvider

interface DynamicDependencies {
  val beersRepository: BeersRepository

  val coroutineDispatcher: CoroutineDispatcherProvider
}
