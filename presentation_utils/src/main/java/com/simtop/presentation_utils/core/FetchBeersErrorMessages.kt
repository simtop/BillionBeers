package com.simtop.presentation_utils.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.core.core.CommonUiState
import com.simtop.presentation_utils.R

/**
 * The one user-facing error state per [FetchBeersError], shared by every paged beers screen (the
 * catalog and search used to keep diverging private copies). Known kinds carry a string resource so
 * they localize; only an [FetchBeersError.Unknown] with a cause message falls back to that literal
 * runtime string.
 */
fun FetchBeersError.toErrorState(): CommonUiState.Error =
  when (this) {
    FetchBeersError.Network -> CommonUiState.Error(messageRes = R.string.error_no_internet)
    FetchBeersError.NotFound -> CommonUiState.Error(messageRes = R.string.error_no_beers_found)
    FetchBeersError.Forbidden -> CommonUiState.Error(messageRes = R.string.error_access_denied)
    FetchBeersError.RateLimited -> CommonUiState.Error(messageRes = R.string.error_rate_limited)
    is FetchBeersError.Unknown ->
      cause.message?.let { CommonUiState.Error(message = it) }
        ?: CommonUiState.Error(messageRes = R.string.error_failed_to_load_beers)
  }

/** Resolves an error to displayable text: the literal message, else the localized resource. */
@Composable
fun CommonUiState.Error.resolvedMessage(): String? =
  message ?: messageRes?.let { stringResource(it) }
