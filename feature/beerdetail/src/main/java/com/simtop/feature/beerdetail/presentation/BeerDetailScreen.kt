@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simtop.feature.beerdetail.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.simtop.billionbeers.core.designsystem.component.showToast
import com.simtop.billionbeers.shared.beerdetail.BeerDetailEvent as SharedBeerDetailEvent
import com.simtop.billionbeers.shared.designsystem.controls.BrandCircularProgressIndicator
import com.simtop.billionbeers.shared.presentation.LocalOnboardingHints
import com.simtop.billionbeers.shared.presentation.OnboardingIntroductions
import com.simtop.core.core.CommonUiState
import com.simtop.core.di.DynamicDependencies
import com.simtop.feature.beerdetail.presentation.di.FeatureDetailComponent
import com.simtop.presentation_utils.R
import com.simtop.presentation_utils.custom_views.ComposeIntroductionHint
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

@Composable
fun BeerDetailScreenImpl(
  beerId: String,
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier,
  showBackButton: Boolean = true,
) {
  val context = LocalContext.current
  val hints = LocalOnboardingHints.current
  var hintChecked by rememberSaveable(beerId) { mutableStateOf(false) }
  var hintVisible by rememberSaveable(beerId) { mutableStateOf(false) }

  val factory = remember {
    val dependencies = context.applicationContext as DynamicDependencies
    val component = createGraphFactory<FeatureDetailComponent.Factory>().create(dependencies)
    component.metroViewModelFactory
  }

  CompositionLocalProvider(LocalMetroViewModelFactory provides factory) {
    val viewModel: BeerDetailViewModel =
      assistedMetroViewModel<BeerDetailViewModel, BeerDetailViewModel.Factory>(key = beerId) {
        create(beerId)
      }
    val viewState by viewModel.beerDetailViewState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val favoriteUpdateFailedMessage = stringResource(R.string.favorite_update_failed)
    val availabilityUpdateFailedMessage = stringResource(R.string.error_failed_to_load_beers)

    LaunchedEffect(beerId, viewState, hints) {
      val state = viewState
      if (state is CommonUiState.Success && !hintChecked && hints != null) {
        hintChecked = true
        if (!state.data.isFavorite) hintVisible = hints.claim(OnboardingIntroductions.favorites)
      }
    }

    LaunchedEffect(
      viewModel,
      lifecycleOwner,
      favoriteUpdateFailedMessage,
      availabilityUpdateFailedMessage,
    ) {
      lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.events.collect { event ->
          when (event) {
            is SharedBeerDetailEvent.ShowError ->
              showToast(
                context = context,
                message =
                  when (event.error) {
                    com.simtop.billionbeers.shared.beerdetail.BeerDetailError.FavoriteUpdate ->
                      favoriteUpdateFailedMessage
                    com.simtop.billionbeers.shared.beerdetail.BeerDetailError.AvailabilityUpdate ->
                      availabilityUpdateFailedMessage
                  },
              )
            SharedBeerDetailEvent.FavoriteUpdated -> hintVisible = false
          }
        }
      }
    }

    when (val state = viewState) {
      is CommonUiState.Success -> {
        ComposeBeerDetail(
          beer = state.data,
          onBackClick = onBackClick,
          onToggleAvailability = viewModel::updateAvailability,
          onToggleFavorite = viewModel::updateFavorite,
          modifier = modifier,
          showBackButton = showBackButton,
          favoriteHint = {
            OnboardingFavoriteHint(
              visible = hintVisible && hints != null,
              onDismiss = { hintVisible = false },
            )
          },
        )
      }
      is CommonUiState.Error ->
        ComposeBeerDetailLoadError(
          onBackClick = onBackClick,
          onRetry = viewModel::retryLoad,
          modifier = modifier,
          showBackButton = showBackButton,
        )
      CommonUiState.Loading -> {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          BrandCircularProgressIndicator()
        }
      }
      else -> {}
    }
  }
}

@Composable
private fun OnboardingFavoriteHint(
  visible: Boolean,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (visible) {
    ComposeIntroductionHint(
      text = stringResource(R.string.onboarding_favorite_hint),
      dismissLabel = stringResource(R.string.onboarding_got_it),
      onDismiss = onDismiss,
      modifier = modifier,
    )
  }
}
