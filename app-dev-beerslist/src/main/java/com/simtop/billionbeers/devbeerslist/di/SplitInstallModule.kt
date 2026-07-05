package com.simtop.billionbeers.devbeerslist.di

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.simtop.core.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

// BeersListScreen's click handler goes through DynamicFeatureLoader unconditionally, so it needs
// a real SplitInstallManager even though :feature:beerdetail isn't a dynamic feature of this app -
// the install attempt just fails harmlessly (see README.md in this module).
@ContributesTo(AppScope::class)
interface SplitInstallModule {

  @Provides
  @SingleIn(AppScope::class)
  fun provideSplitInstallManager(@ApplicationContext context: Context): SplitInstallManager {
    return SplitInstallManagerFactory.create(context)
  }
}
