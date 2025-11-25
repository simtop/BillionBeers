package com.simtop.core.core

sealed class CommonUiState<out T> {
    object Loading : CommonUiState<Nothing>()
    data class Success<T>(val data: T) : CommonUiState<T>()
    data class Error(val message: String? = null) : CommonUiState<Nothing>()
    object Empty : CommonUiState<Nothing>()
}