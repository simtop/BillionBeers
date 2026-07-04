package com.simtop.core.di

import com.simtop.core.core.DefaultLanguageProvider
import com.simtop.core.core.LanguageProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface LanguageModule {

  @Provides
  @SingleIn(AppScope::class)
  fun providesLanguageProvider(): LanguageProvider = DefaultLanguageProvider()
}
