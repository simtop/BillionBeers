package com.simtop.billionbeers.di

import android.content.Context
import com.simtop.billionbeers.BuildConfig
import com.simtop.core.core.EnvironmentConfig
import com.simtop.core.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** The app-owned environment boundary; hosts can replace this module with their own values. */
@ContributesTo(AppScope::class)
interface EnvironmentModule {

  companion object {
    @Provides
    @SingleIn(AppScope::class)
    fun provideApiEnvironmentController(
      @ApplicationContext context: Context
    ): ApiEnvironmentController =
      DefaultApiEnvironmentController(
        context = context,
        debugSelectionEnabled = BuildConfig.DEBUG,
      )

    @Provides
    @SingleIn(AppScope::class)
    fun provideEnvironmentConfig(
      apiEnvironmentController: ApiEnvironmentController
    ): EnvironmentConfig {
      val selectedEnvironment = apiEnvironmentController.selectedEnvironment.value
      val config = EnvironmentConfig(apiBaseUrl = selectedEnvironment.apiBaseUrl)
      if (!BuildConfig.DEBUG) {
        check(selectedEnvironment == ApiEnvironment.PRODUCTION) {
          "Release builds must use the Production API environment"
        }
        config.validateForRelease(ApiEnvironment.PRODUCTION.apiBaseUrl)
      }
      return config
    }
  }
}
