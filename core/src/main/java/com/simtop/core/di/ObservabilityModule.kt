package com.simtop.core.di

import com.simtop.core.core.AnalyticsTracker
import com.simtop.core.core.CrashReporter
import com.simtop.core.core.Logger
import com.simtop.core.core.NoOpAnalyticsTracker
import com.simtop.core.core.NoOpCrashReporter
import com.simtop.core.observability.AndroidLogcatLogger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The account-free observability seam. The default bindings do nothing for analytics and crash
 * reporting; the logger only receives the closed [com.simtop.core.core.Diagnostic] vocabulary. A
 * future adapter may be bound here after a real provider and consent policy exist, without changing
 * call sites or widening the data contract.
 */
@ContributesTo(AppScope::class)
interface ObservabilityModule {

  @Provides @SingleIn(AppScope::class) fun providesLogger(): Logger = AndroidLogcatLogger()

  @Provides
  @SingleIn(AppScope::class)
  fun providesAnalyticsTracker(): AnalyticsTracker = NoOpAnalyticsTracker()

  @Provides
  @SingleIn(AppScope::class)
  fun providesCrashReporter(): CrashReporter = NoOpCrashReporter()
}
