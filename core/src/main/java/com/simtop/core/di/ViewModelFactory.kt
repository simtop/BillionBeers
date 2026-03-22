package com.simtop.core.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider

import kotlin.reflect.KClass

class ViewModelFactory @Inject constructor(
    private val viewModelMap: Map<KClass<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val creator = viewModelMap[modelClass.kotlin] ?: viewModelMap.entries.firstOrNull {
            modelClass.kotlin.java.isAssignableFrom(it.key.java)
        }?.value ?: throw IllegalArgumentException("unknown model class $modelClass")
        return (creator.invoke() as T)
    }
}
