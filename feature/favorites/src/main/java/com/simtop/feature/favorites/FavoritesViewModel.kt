package com.simtop.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.CommonUiState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val FAVORITES_STOP_TIMEOUT_MILLIS = 5_000L

@ContributesIntoMap(AppScope::class)
@ViewModelKey(FavoritesViewModel::class)
@Inject
class FavoritesViewModel(private val beersRepository: BeersRepository) : ViewModel() {

  val viewState: StateFlow<CommonUiState<List<Beer>>> =
    beersRepository
      .observeFavoriteBeers()
      .map { favorites ->
        if (favorites.isEmpty()) CommonUiState.Empty else CommonUiState.Success(favorites)
      }
      .catch { emit(CommonUiState.Error(message = it.message)) }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(FAVORITES_STOP_TIMEOUT_MILLIS),
        CommonUiState.Loading,
      )
}
