package com.simtop.feature.favorites

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.shared.favorites.SharedFavoritesContent
import com.simtop.core.core.CommonUiState
import com.simtop.presentation_utils.R as PresentationUtilsR
import com.simtop.presentation_utils.custom_views.ComposeBeersListItem
import dev.zacsweers.metrox.viewmodel.metroViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
  onBeerClick: (Beer) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: FavoritesViewModel = metroViewModel(),
) {
  val viewState by viewModel.viewState.collectAsState()
  FavoritesContent(viewState = viewState, onBeerClick = onBeerClick, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesContent(
  viewState: CommonUiState<List<Beer>>,
  onBeerClick: (Beer) -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(title = { Text(stringResource(PresentationUtilsR.string.favorites_title)) })
    },
  ) { paddingValues ->
    SharedFavoritesContent(
      viewState = viewState,
      emptyText = stringResource(PresentationUtilsR.string.favorites_empty),
      errorText = stringResource(PresentationUtilsR.string.error_failed_to_load_beers),
      beerRow = { beer -> ComposeBeersListItem(beer = beer, onClick = onBeerClick) },
      modifier = Modifier.fillMaxSize().consumeWindowInsets(paddingValues).padding(paddingValues),
    )
  }
}

@PreviewLightDark
@Composable
internal fun FavoritesEmptyPreview() {
  BillionBeersTheme {
    FavoritesContent(viewState = CommonUiState.Empty, onBeerClick = {})
  }
}
