package com.simtop.feature.beerdetail.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simtop.billionbeers.shared.beerdetail.SharedBeerDetailLoadError
import com.simtop.billionbeers.shared.designsystem.controls.BrandIcon
import com.simtop.presentation_utils.R
import com.simtop.presentation_utils.custom_views.ComposeErrorView

@Composable
internal fun ComposeBeerDetailLoadError(
  onBackClick: () -> Unit,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
  showBackButton: Boolean = true,
) {
  SharedBeerDetailLoadError(
    onBackClick = onBackClick,
    backIcon = {
      BrandIcon(
        Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(R.string.beer_detail_back),
      )
    },
    errorContent = {
      ComposeErrorView(
        message = stringResource(R.string.error_failed_to_load_beers),
        onRetry = onRetry,
      )
    },
    modifier = modifier,
    showBackButton = showBackButton,
  )
}
