package com.simtop.presentation_utils.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simtop.core.di.GraphProvider

@Composable
inline fun <reified T : ViewModel> metroViewModel(): T {
    val context = LocalContext.current
    val graphProvider = context.applicationContext as GraphProvider
    return viewModel(factory = graphProvider.viewModelFactory)
}

@Composable
inline fun <reified VM : ViewModel> assistedViewModel(
    crossinline create: () -> VM
): VM {
    val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return create() as T
        }
    }
    return viewModel(factory = factory)
}
