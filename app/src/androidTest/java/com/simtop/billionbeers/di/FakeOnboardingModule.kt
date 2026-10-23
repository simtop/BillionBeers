package com.simtop.billionbeers.di

import com.simtop.billionbeers.presentation.OnboardingController
import com.simtop.billionbeers.presentation.OnboardingStore
import com.simtop.presentation_utils.core.OnboardingIntroductions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Existing navigation suites begin after onboarding. Individual onboarding tests reset explicitly.
 */
@BindingContainer
@ContributesTo(AppScope::class, replaces = [OnboardingModule::class])
object FakeOnboardingModule {
  @Provides
  @SingleIn(AppScope::class)
  fun provideOnboardingController(): OnboardingController =
    OnboardingController(
      object : OnboardingStore {
        private var saved =
          setOf(OnboardingIntroductions.welcome.value, OnboardingIntroductions.favorites.value)

        override suspend fun read() = saved

        override suspend fun write(completedIds: Set<String>) {
          saved = completedIds
        }
      },
      CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )
}
