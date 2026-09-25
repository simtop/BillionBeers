package com.simtop.billionbeers.shared.beerbrowse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.Brewery
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.billionbeers.shared.presentation.toCommonUiErrorState
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.Either
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

open class BrowseViewModel(private val beersRepository: BeersRepository) : ViewModel() {

  private val _styles = MutableStateFlow<CommonUiState<List<BeerStyle>>>(CommonUiState.Loading)
  val styles: StateFlow<CommonUiState<List<BeerStyle>>> = _styles.asStateFlow()

  private val _breweries = MutableStateFlow<CommonUiState<List<Brewery>>>(CommonUiState.Loading)
  val breweries: StateFlow<CommonUiState<List<Brewery>>> = _breweries.asStateFlow()

  private var breweriesRequested = false

  init {
    retryStyles()
  }

  fun onBreweriesTabSelected() {
    if (breweriesRequested) return
    breweriesRequested = true
    retryBreweries()
  }

  fun retryStyles() {
    viewModelScope.launch {
      _styles.value = CommonUiState.Loading
      _styles.value = beersRepository.getBeerStyles().toUiState()
    }
  }

  fun retryBreweries() {
    viewModelScope.launch {
      _breweries.value = CommonUiState.Loading
      _breweries.value = beersRepository.getBreweries().toUiState()
    }
  }

  private fun <T> Either<FetchBeersError, List<T>>.toUiState(): CommonUiState<List<T>> =
    either(
      fnL = { error -> error.toCommonUiErrorState() },
      fnR = { list -> if (list.isEmpty()) CommonUiState.Empty else CommonUiState.Success(list) },
    )
}
