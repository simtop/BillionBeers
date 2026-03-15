package com.simtop.core.core

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A helper class to handle PagingState updates in ViewModels. It helps reduce boilerplate when
 * mapping PagingState to UI State.
 */
class PagingHandler<T>(
  private val uiState: MutableStateFlow<T>,
  private val reduce: (T, PagingState) -> T
) {
  fun handlePagingState(pagingState: PagingState) {
    val currentState = uiState.value
    val newState = reduce(currentState, pagingState)
    uiState.value = newState
  }
}
