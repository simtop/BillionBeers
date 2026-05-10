package com.simtop.core.di

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding

@ContributesBinding(AppScope::class, binding = binding<MetroViewModelFactory>())
class DefaultMetroViewModelFactory @Inject constructor(
    override val viewModelProviders: Map<KClass<out ViewModel>, Provider<ViewModel>>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, Provider<ViewModelAssistedFactory>>,
    override val manualAssistedFactoryProviders: Map<KClass<out ManualViewModelAssistedFactory>, Provider<ManualViewModelAssistedFactory>>,
) : MetroViewModelFactory()
