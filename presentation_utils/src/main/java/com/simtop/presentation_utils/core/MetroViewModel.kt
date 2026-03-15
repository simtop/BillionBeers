package com.simtop.presentation_utils.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simtop.core.di.GraphProvider

@Composable
inline fun <reified T : ViewModel> metroViewModel(): T {
    val context = LocalContext.current
    val graphProvider = context.applicationContext as GraphProvider
    return viewModel(factory = graphProvider.viewModelFactory)
}
