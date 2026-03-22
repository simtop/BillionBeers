package com.simtop.feature.beerdetail.presentation.di

import com.simtop.billionbeers.di.DynamicDependencies
import com.simtop.feature.beerdetail.presentation.BeerDetailViewModel
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes

@DependencyGraph
interface FeatureDetailComponent {

  fun getViewModelFactory(): BeerDetailViewModel.Factory

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(
      @Includes dependencies: DynamicDependencies,
    ): FeatureDetailComponent
  }
}
