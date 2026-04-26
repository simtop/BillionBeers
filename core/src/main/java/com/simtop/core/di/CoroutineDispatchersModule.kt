package com.simtop.core.di

import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.DefaultCoroutineDispatcherProvider
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface CoroutineDispatchersModule {

  @Provides
  @SingleIn(AppScope::class)
  fun providesDispatcherProvider(): CoroutineDispatcherProvider =
    DefaultCoroutineDispatcherProvider()
}
