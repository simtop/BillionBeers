package com.simtop.core.core

enum class CommonUiErrorKey {
  NoInternet,
  NoBeersFound,
  AccessDenied,
  RateLimited,
  FailedToLoadBeers,
}

sealed class CommonUiState<out T> {
  object Loading : CommonUiState<Nothing>()

  data class Success<T>(val data: T) : CommonUiState<T>()

  /**
   * [message] is a literal runtime string (e.g. an exception message); [errorKey] identifies a
   * known error kind for host-side localization. When both are null the screen uses generic copy.
   */
  data class Error(val message: String? = null, val errorKey: CommonUiErrorKey? = null) :
    CommonUiState<Nothing>()

  object Empty : CommonUiState<Nothing>()
}
