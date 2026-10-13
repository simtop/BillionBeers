package com.simtop.billionbeers.shared.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.retryWithErrorState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val FAVORITES_STOP_TIMEOUT_MILLIS = 5_000L

open class FavoritesViewModel(private val beersRepository: BeersRepository) : ViewModel() {

  val viewState: StateFlow<CommonUiState<List<Beer>>> =
    beersRepository
      .observeFavoriteBeers()
      .map { favorites ->
        if (favorites.isEmpty()) CommonUiState.Empty else CommonUiState.Success(favorites)
      }
      .retryWithErrorState { CommonUiState.Error() }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(FAVORITES_STOP_TIMEOUT_MILLIS),
        CommonUiState.Loading,
      )
}
