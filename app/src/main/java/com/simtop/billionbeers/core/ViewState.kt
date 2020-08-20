package com.simtop.billionbeers.core

sealed class ViewState<out L, out R> {
    data class Result<out L, out R>(val result: Either<L, R>) : ViewState<L, R>()
    object Loading : ViewState<Nothing, Nothing>()
    object EmptyState : ViewState<Nothing, Nothing>()
}