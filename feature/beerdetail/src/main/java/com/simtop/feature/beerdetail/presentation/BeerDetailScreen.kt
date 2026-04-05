package com.simtop.feature.beerdetail.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.core.core.CommonUiState
import com.simtop.presentation_utils.core.showToast
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.simtop.billionbeers.BillionBeersApplication
import com.simtop.billionbeers.di.DynamicDependencies
import com.simtop.feature.beerdetail.presentation.di.FeatureDetailComponent
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory

@Composable
fun BeerDetailScreenImpl(beer: Beer, onBackClick: () -> Unit) {
  val context = LocalContext.current

  val factory = remember {
    val appGraph = (context.applicationContext as BillionBeersApplication).appGraph as DynamicDependencies
    val component = createGraphFactory<FeatureDetailComponent.Factory>().create(appGraph)
    component.metroViewModelFactory
  }

  CompositionLocalProvider(LocalMetroViewModelFactory provides factory) {
    val viewModel: BeerDetailViewModel = assistedMetroViewModel<BeerDetailViewModel, BeerDetailViewModel.Factory> { create(beer) }
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
}
