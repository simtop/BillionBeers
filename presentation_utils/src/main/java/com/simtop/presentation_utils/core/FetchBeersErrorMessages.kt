package com.simtop.presentation_utils.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.billionbeers.shared.presentation.toCommonUiErrorState
import com.simtop.core.core.CommonUiErrorKey
import com.simtop.core.core.CommonUiState
import com.simtop.presentation_utils.R

/**
 * The one user-facing error state per [FetchBeersError], shared by every paged beers screen (the
 * catalog and search used to keep diverging private copies). Known kinds carry a semantic key for
 * host-side localization; unknown causes are intentionally mapped to generic localized copy rather
 * than exposing raw exception text.
 */
fun FetchBeersError.toErrorState(): CommonUiState.Error = toCommonUiErrorState()

/** Resolves an error to displayable text: the literal message, else the localized resource. */
@Composable
fun CommonUiState.Error.resolvedMessage(): String? =
  message
    ?: errorKey?.let { key ->
      stringResource(
        when (key) {
          CommonUiErrorKey.NoInternet -> R.string.error_no_internet
          CommonUiErrorKey.NoBeersFound -> R.string.error_no_beers_found
          CommonUiErrorKey.AccessDenied -> R.string.error_access_denied
          CommonUiErrorKey.RateLimited -> R.string.error_rate_limited
          CommonUiErrorKey.FailedToLoadBeers -> R.string.error_failed_to_load_beers
        }
      )
    }
    ?: stringResource(R.string.error_failed_to_load_beers)
