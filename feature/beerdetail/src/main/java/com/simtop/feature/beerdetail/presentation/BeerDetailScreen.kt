package com.simtop.feature.beerdetail.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.di.DynamicDependencies
import com.simtop.core.core.CommonUiState
import com.simtop.feature.beerdetail.presentation.di.DaggerFeatureDetailComponent
import com.simtop.presentation_utils.core.showToast
import dagger.hilt.android.EntryPointAccessors

@Composable
fun BeerDetailScreenImpl(
    beer: Beer,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    val factory = remember(beer) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val deps = EntryPointAccessors.fromApplication(context, DynamicDependencies::class.java)
                val component = DaggerFeatureDetailComponent.factory().create(deps)
                return component.getViewModelFactory().create(beer) as T
            }
        }
    }

    val viewModel: BeerDetailViewModel = viewModel(factory = factory)
    val viewState by viewModel.beerDetailViewState.collectAsState()

    when (val state = viewState) {
        is CommonUiState.Success -> {
            ComposeBeerDetail(
                beer = state.data,
                onBackClick = onBackClick,
                onToggleAvailability = { viewModel.updateAvailability(state.data) }
            )
        }
        is CommonUiState.Error -> {
            state.message?.let { context.showToast(it) }
        }
        CommonUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        else -> {}
    }
}
