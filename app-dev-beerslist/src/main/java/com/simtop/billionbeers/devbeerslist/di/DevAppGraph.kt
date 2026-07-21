package com.simtop.billionbeers.devbeerslist.di

import android.content.Context
import com.simtop.core.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelMultibindings

@DependencyGraph(AppScope::class)
interface DevAppGraph : MetroViewModelMultibindings {
  val metroViewModelFactory: MetroViewModelFactory

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Provides @ApplicationContext context: Context): DevAppGraph
  }
}
