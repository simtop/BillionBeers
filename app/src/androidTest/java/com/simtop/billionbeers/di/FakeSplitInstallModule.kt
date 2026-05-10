package com.simtop.billionbeers.di

import com.google.android.play.core.splitinstall.SplitInstallManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import com.simtop.billionbeers.fakes.FakeSplitInstallManager

@BindingContainer
@ContributesTo(AppScope::class, replaces = [SplitInstallModule::class])
object FakeSplitInstallModule {
    val fakeSplitInstallManager = FakeSplitInstallManager()

    @Provides
    @SingleIn(AppScope::class)
    fun provideSplitInstallManager(): SplitInstallManager = fakeSplitInstallManager
}