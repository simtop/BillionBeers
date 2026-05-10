package com.simtop.billionbeers.di

import android.content.Context
import dev.zacsweers.metro.AppScope
import com.simtop.core.di.ApplicationContext
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides

@DependencyGraph(AppScope::class)
interface AppGraph : BaseAppGraph {

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides @ApplicationContext context: Context): AppGraph
    }
}
