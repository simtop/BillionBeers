package com.simtop.feature.beerbrowse.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.Brewery
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.Either
import com.simtop.feature.beerbrowse.presentation.di.FeatureBrowseScope
import com.simtop.presentation_utils.core.toErrorState
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The browse landing screen: 17 styles and 38 breweries, each a single unpaged fetch (deliberately
 * no pager - see rod plan §6.4's "knowing when not to plug it in"). Styles load immediately (the
 * first tab on screen); breweries wait for their tab to be selected once, so a user who never
 * switches tabs never spends the request.
 */
@ContributesIntoMap(FeatureBrowseScope::class)
@ViewModelKey(BrowseViewModel::class)
@Inject
class BrowseViewModel(
  private val coroutineDispatcher: CoroutineDispatcherProvider,
  private val beersRepository: BeersRepository,
) : ViewModel() {

  private val _styles = MutableStateFlow<CommonUiState<List<BeerStyle>>>(CommonUiState.Loading)
  val styles: StateFlow<CommonUiState<List<BeerStyle>>> = _styles.asStateFlow()

  private val _breweries = MutableStateFlow<CommonUiState<List<Brewery>>>(CommonUiState.Loading)
  val breweries: StateFlow<CommonUiState<List<Brewery>>> = _breweries.asStateFlow()

  private var breweriesRequested = false

  init {
    retryStyles()
  }

  /** First selection triggers the fetch; later selections are free tab switches. */
  fun onBreweriesTabSelected() {
    if (breweriesRequested) return
    breweriesRequested = true
    retryBreweries()
  }

  fun retryStyles() {
    viewModelScope.launch(coroutineDispatcher.io) {
      _styles.value = CommonUiState.Loading
      _styles.value = beersRepository.getBeerStyles().toUiState()
    }
  }

  fun retryBreweries() {
    viewModelScope.launch(coroutineDispatcher.io) {
      _breweries.value = CommonUiState.Loading
      _breweries.value = beersRepository.getBreweries().toUiState()
    }
  }

  private fun <T> Either<FetchBeersError, List<T>>.toUiState(): CommonUiState<List<T>> =
    either(
      fnL = { error -> error.toErrorState() },
      fnR = { list -> if (list.isEmpty()) CommonUiState.Empty else CommonUiState.Success(list) },
    )
}
