package com.simtop.billionbeers.di

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.simtop.beerdomain.domain.usecases.UpdateAvailabilityUseCase
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.di.AppScope
import com.simtop.core.di.ApplicationContext
import com.simtop.core.di.ViewModelFactory
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides

@DependencyGraph(AppScope::class)
interface AppGraph : BaseAppGraph {

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides @ApplicationContext context: Context): AppGraph
    }
}
