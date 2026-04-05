package com.simtop.billionbeers.di

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

@ContributesTo(AppScope::class)
interface ViewModelMapsModule {
    @Multibinds(allowEmpty = true)
    fun viewModels(): Map<KClass<out ViewModel>, Provider<ViewModel>>

    @Multibinds(allowEmpty = true)
    fun assistedViewModels(): Map<KClass<out ViewModel>, Provider<ViewModelAssistedFactory>>

    @Multibinds(allowEmpty = true)
    fun manualAssistedViewModels(): Map<KClass<out ManualViewModelAssistedFactory>, Provider<ManualViewModelAssistedFactory>>
}
