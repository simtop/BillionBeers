package com.simtop.billionbeers.di

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import dev.zacsweers.metro.AppScope
import com.simtop.core.di.ApplicationContext
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface SplitInstallModule {

  @Provides
  @SingleIn(AppScope::class)
  fun provideSplitInstallManager(@ApplicationContext context: Context): SplitInstallManager {
    return SplitInstallManagerFactory.create(context)
  }
}
