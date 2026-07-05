package com.simtop.billionbeers.devbeerslist.di

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

// Same shape as the main app's ViewModelMapsModule - this is a separate application module (not
// a dependency of :app), so it needs its own copy for Metro's multibinding maps to resolve.
@ContributesTo(AppScope::class)
interface ViewModelMapsModule {
  @Multibinds(allowEmpty = true) fun viewModels(): Map<KClass<out ViewModel>, ViewModel>

  @Multibinds(allowEmpty = true)
  fun assistedViewModels(): Map<KClass<out ViewModel>, ViewModelAssistedFactory>

  @Multibinds(allowEmpty = true)
  fun manualAssistedViewModels():
    Map<KClass<out ManualViewModelAssistedFactory>, ManualViewModelAssistedFactory>
}
