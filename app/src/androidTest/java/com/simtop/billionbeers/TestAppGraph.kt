package com.simtop.billionbeers

import android.content.Context
import com.simtop.billionbeers.di.BaseAppGraph
import dev.zacsweers.metro.AppScope
import com.simtop.core.di.ApplicationContext
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = []
)
interface TestAppGraph : BaseAppGraph {

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides @ApplicationContext context: Context,
        ): TestAppGraph
    }
}
