package com.simtop.core.core

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A helper class to handle PagingState updates in ViewModels. It helps reduce boilerplate when
 * mapping PagingState to UI State.
 */
class PagingHandler<T, Error : Any>(
  private val uiState: MutableStateFlow<T>,
  private val reduce: (T, PagingState<Error>) -> T,
) {
  fun handlePagingState(pagingState: PagingState<Error>) {
    val currentState = uiState.value
    val newState = reduce(currentState, pagingState)
    uiState.value = newState
  }
}
