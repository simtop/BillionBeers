package com.simtop.feature.beerdetail.presentation.di

import com.simtop.billionbeers.di.DynamicDependencies
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes
import com.simtop.core.di.DefaultMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelMultibindings

@DependencyGraph(FeatureDetailScope::class)
interface FeatureDetailComponent : MetroViewModelMultibindings {
  val metroViewModelFactory: DefaultMetroViewModelFactory

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Includes dependencies: DynamicDependencies): FeatureDetailComponent
  }
}
