package com.simtop.billionbeers.di

import android.content.Context
import com.simtop.core.di.AppScope
import com.simtop.core.di.ApplicationContext
import com.simtop.core.di.DefaultMetroViewModelFactory
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides

@DependencyGraph(AppScope::class)
interface AppGraph : BaseAppGraph {

    override val metroViewModelFactory: DefaultMetroViewModelFactory

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides @ApplicationContext context: Context): AppGraph
    }
}
