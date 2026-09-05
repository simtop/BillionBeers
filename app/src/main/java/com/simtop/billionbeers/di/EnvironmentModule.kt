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
      val releaseSmokeBaseUrl = BuildConfig.RELEASE_SMOKE_API_BASE_URL
      val config =
        EnvironmentConfig(
          apiBaseUrl = releaseSmokeBaseUrl.ifBlank { selectedEnvironment.apiBaseUrl }
        )
      if (!BuildConfig.DEBUG) {
        if (BuildConfig.BUILD_TYPE == RELEASE_SMOKE_BUILD_TYPE) {
          // The smoke target uses a local server, but still exercises the release guard against
          // the exact production endpoint before swapping in that deterministic test endpoint.
          EnvironmentConfig(apiBaseUrl = ApiEnvironment.PRODUCTION.apiBaseUrl)
            .validateForRelease(expectedApiBaseUrl = ApiEnvironment.PRODUCTION.apiBaseUrl)
        } else {
          check(selectedEnvironment == ApiEnvironment.PRODUCTION) {
            "Release builds must use the Production API environment"
          }
          config.validateForRelease(ApiEnvironment.PRODUCTION.apiBaseUrl)
        }
      }
      return config
    }

    private const val RELEASE_SMOKE_BUILD_TYPE = "releaseSmoke"
  }
}
