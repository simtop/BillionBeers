package com.simtop.feature.beerbrowse.presentation.di

import com.simtop.core.di.DefaultMetroViewModelFactory
import com.simtop.core.di.DynamicDependencies
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
