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
 * The observability seam: swap [NoOpAnalyticsTracker] / [NoOpCrashReporter] for real backends
 * (Firebase Analytics, Crashlytics, Sentry, ...) here, one binding at a time, without touching any
 * call site.
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
