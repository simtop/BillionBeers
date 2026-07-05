package com.simtop.core.di

import com.simtop.core.core.DefaultNetworkFaultController
import com.simtop.core.core.DefaultThemeController
import com.simtop.core.core.FeatureFlagProvider
import com.simtop.core.core.LocalFeatureFlagProvider
import com.simtop.core.core.NetworkFaultController
import com.simtop.core.core.ThemeController
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Bindings backing the debug drawer. Registered in every build type - the drawer UI itself
 * (app/src/debug) is what's actually gated to debug builds; [NetworkFaultController] and
 * [FeatureFlagProvider] always exist so production code can read them cheaply (fault mode stays
 * NONE and flags stay at their defaults outside debug, since nothing ever calls setMode/setOverride).
 */
@ContributesTo(AppScope::class)
interface DebugModule {

  @Provides
  @SingleIn(AppScope::class)
  fun providesNetworkFaultController(): NetworkFaultController = DefaultNetworkFaultController()

  @Provides
  @SingleIn(AppScope::class)
  fun providesFeatureFlagProvider(): FeatureFlagProvider = LocalFeatureFlagProvider()

  @Provides
  @SingleIn(AppScope::class)
  fun providesThemeController(): ThemeController = DefaultThemeController()
}
