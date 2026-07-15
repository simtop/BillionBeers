package com.simtop.core.core

sealed class CommonUiState<out T> {
  object Loading : CommonUiState<Nothing>()

  data class Success<T>(val data: T) : CommonUiState<T>()

  /**
   * [message] is a literal runtime string (e.g. an exception message); [messageRes] is an Android
   * string resource id, used for known error kinds so they localize. When both are null the screen
   * falls back to its generic error copy.
   */
  data class Error(val message: String? = null, val messageRes: Int? = null) :
    CommonUiState<Nothing>()

  object Empty : CommonUiState<Nothing>()
}
