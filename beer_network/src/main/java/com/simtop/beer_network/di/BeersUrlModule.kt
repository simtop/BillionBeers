package com.simtop.beer_network.di

import com.simtop.core.di.AppScope
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import com.simtop.core.di.Named

@ContributesTo(AppScope::class)
interface BeersUrlModule {

  @Provides
  @SingleIn(AppScope::class)
  @Named("baseUrl")
  fun provideBaseUrl(): String {
    return "https://brewbuddy.dev/?translations.language.code=en"
  }
}
