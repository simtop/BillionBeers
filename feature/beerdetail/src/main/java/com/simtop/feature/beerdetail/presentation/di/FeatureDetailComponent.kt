package com.simtop.feature.beerdetail.presentation.di

import androidx.lifecycle.ViewModel
import com.simtop.billionbeers.di.DynamicDependencies
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

import dev.zacsweers.metro.ContributesTo

@ContributesTo(FeatureDetailScope::class)
@BindingContainer
interface FeatureDetailModule {
  @Multibinds(allowEmpty = true)
  fun viewModels(): Map<KClass<out ViewModel>, Provider<ViewModel>>

  @Multibinds(allowEmpty = true)
  fun assistedViewModels(): Map<KClass<out ViewModel>, Provider<ViewModelAssistedFactory>>

  @Multibinds(allowEmpty = true)
  fun manualAssistedViewModels(): Map<KClass<out ManualViewModelAssistedFactory>, Provider<ManualViewModelAssistedFactory>>
}

@DependencyGraph(FeatureDetailScope::class)
interface FeatureDetailComponent {

  val metroViewModelFactory: FeatureDetailViewModelFactory

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(
      @Includes dependencies: DynamicDependencies,
    ): FeatureDetailComponent
  }
}
