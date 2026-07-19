package com.simtop.feature.beerbrowse.presentation.di

import com.simtop.billionbeers.di.DynamicDependencies
import com.simtop.core.di.DefaultMetroViewModelFactory
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes
import dev.zacsweers.metrox.viewmodel.MetroViewModelMultibindings

@DependencyGraph(FeatureBrowseScope::class)
interface FeatureBrowseComponent : MetroViewModelMultibindings {
  val metroViewModelFactory: DefaultMetroViewModelFactory

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Includes dependencies: DynamicDependencies): FeatureBrowseComponent
  }
}
