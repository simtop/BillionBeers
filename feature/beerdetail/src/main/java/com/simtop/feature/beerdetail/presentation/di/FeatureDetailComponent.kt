package com.simtop.feature.beerdetail.presentation.di

import com.simtop.billionbeers.di.DynamicDependencies
import com.simtop.core.di.DefaultMetroViewModelFactory
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

@ContributesTo(FeatureDetailScope::class)
@BindingContainer
interface FeatureDetailModule {
  @Multibinds(allowEmpty = true)
  fun viewModels(): Map<KClass<out ViewModel>, ViewModel>

  @Multibinds(allowEmpty = true)
  fun assistedViewModels(): Map<KClass<out ViewModel>, ViewModelAssistedFactory>

  @Multibinds(allowEmpty = true)
  fun manualAssistedViewModels(): Map<KClass<out ManualViewModelAssistedFactory>, ManualViewModelAssistedFactory>
}

@DependencyGraph(FeatureDetailScope::class)
interface FeatureDetailComponent {

  val metroViewModelFactory: DefaultMetroViewModelFactory

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(
      @Includes dependencies: DynamicDependencies,
    ): FeatureDetailComponent
  }
}
