package com.simtop.presentation_utils.core

import com.simtop.beerdomain.domain.errors.FetchBeersError

/**
 * The one user-facing message per [FetchBeersError], shared by every paged beers screen (the
 * catalog and search used to keep diverging private copies). Plain strings for now - threading
 * resource ids through `CommonUiState.Error` is the known localization gap.
 */
fun FetchBeersError.toUiMessage(): String =
  when (this) {
    FetchBeersError.Network -> "No internet connection"
    FetchBeersError.NotFound -> "No beers found"
    FetchBeersError.Forbidden -> "Access denied"
    FetchBeersError.RateLimited -> "Too many requests. Please wait a moment."
    is FetchBeersError.Unknown -> cause.message ?: "Failed to load beers"
  }
