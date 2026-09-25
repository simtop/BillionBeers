package com.simtop.billionbeers.shared.presentation

import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.core.core.CommonUiErrorKey
import com.simtop.core.core.CommonUiState

fun FetchBeersError.toCommonUiErrorState(): CommonUiState.Error =
  when (this) {
    FetchBeersError.Network -> CommonUiState.Error(errorKey = CommonUiErrorKey.NoInternet)
    FetchBeersError.NotFound -> CommonUiState.Error(errorKey = CommonUiErrorKey.NoBeersFound)
    FetchBeersError.Forbidden -> CommonUiState.Error(errorKey = CommonUiErrorKey.AccessDenied)
    FetchBeersError.RateLimited -> CommonUiState.Error(errorKey = CommonUiErrorKey.RateLimited)
    is FetchBeersError.Unknown -> CommonUiState.Error(errorKey = CommonUiErrorKey.FailedToLoadBeers)
  }
