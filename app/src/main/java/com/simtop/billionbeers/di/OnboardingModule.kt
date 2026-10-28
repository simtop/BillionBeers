package com.simtop.billionbeers.di

import android.content.Context
import com.simtop.billionbeers.shared.presentation.OnboardingController
import com.simtop.billionbeers.shared.presentation.OnboardingStore
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

@ContributesTo(AppScope::class)
interface OnboardingModule {
  companion object {
    @Provides
    @SingleIn(AppScope::class)
    fun provideOnboardingController(
      @ApplicationContext context: Context,
      dispatchers: CoroutineDispatcherProvider,
    ): OnboardingController =
      OnboardingController(
        AndroidOnboardingStore(context, dispatchers.io),
        CoroutineScope(SupervisorJob() + dispatchers.main),
      )
  }
}

internal class AndroidOnboardingStore(
  private val context: Context,
  private val io: CoroutineDispatcher,
) : OnboardingStore {
  override suspend fun read(): Set<String> =
    withContext(io) {
      val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      preferences.getStringSet(COMPLETED_IDS, emptySet()).orEmpty().toSet()
    }

  override suspend fun write(completedIds: Set<String>) {
    withContext(io) {
      val saved =
        context
          .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
          .edit()
          .putStringSet(COMPLETED_IDS, completedIds)
          .commit()
      if (!saved) throw IOException("Could not save onboarding preferences")
    }
  }

  private companion object {
    const val PREFERENCES_NAME = "onboarding"
    const val COMPLETED_IDS = "completed_introduction_ids"
  }
}
