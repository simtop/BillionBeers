package com.simtop.feature.beerdetail.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.repeatOnLifecycle
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.BillionBeersApplication
import com.simtop.billionbeers.core.designsystem.component.showToast
import com.simtop.billionbeers.di.DynamicDependencies
import com.simtop.core.core.CommonUiState
import com.simtop.feature.beerdetail.presentation.di.FeatureDetailComponent
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

@Composable
fun BeerDetailScreenImpl(beer: Beer, onBackClick: () -> Unit, showBackButton: Boolean = true) {
  val context = LocalContext.current

  val factory = remember {
    val appGraph =
      (context.applicationContext as BillionBeersApplication).appGraph as DynamicDependencies
    val component = createGraphFactory<FeatureDetailComponent.Factory>().create(appGraph)
    component.metroViewModelFactory
  }

  CompositionLocalProvider(LocalMetroViewModelFactory provides factory) {
    val viewModel: BeerDetailViewModel =
      assistedMetroViewModel<BeerDetailViewModel, BeerDetailViewModel.Factory>(key = beer.id) {
        extras ->
        // The SavedStateHandle lets the ViewModel restore a toggled beer across process death
        // instead of reverting to the (stale) nav-arg beer this composable was launched with.
        create(beer, extras.createSavedStateHandle())
      }
    val viewState by viewModel.beerDetailViewState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
      lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.events.collect { event ->
          when (event) {
            is BeerDetailEvent.ShowError -> showToast(context = context, message = event.message)
          }
        }
      }
    }

    when (val state = viewState) {
      is CommonUiState.Success -> {
        ComposeBeerDetail(
          beer = state.data,
          onBackClick = onBackClick,
          onToggleAvailability = { viewModel.updateAvailability(state.data) },
          showBackButton = showBackButton,
        )
      }
      is CommonUiState.Error -> Unit
      CommonUiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
      else -> {}
    }
  }
}
