package com.simtop.core.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import javax.inject.Inject
import javax.inject.Provider

import kotlin.reflect.KClass

class ViewModelFactory @Inject constructor(
    private val viewModelMap: Map<KClass<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val creator = viewModelMap[modelClass.kotlin] ?: viewModelMap.entries.firstOrNull {
            modelClass.isAssignableFrom(it.key.java)
        }?.value ?: throw IllegalArgumentException("Unknown ViewModel class $modelClass")
        return creator.get() as T
    }
}
