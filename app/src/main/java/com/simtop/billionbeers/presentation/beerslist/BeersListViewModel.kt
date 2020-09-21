package com.simtop.billionbeers.presentation.beerslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.domain.repository.BeersRepository
import com.simtop.billionbeers.domain.usecases.GetBeersFromApiUseCase
import com.simtop.billionbeers.presentation.beerslist.paging.BeersPagingSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BeersListViewModel @Inject constructor(
    private val getBeersFromApiUseCase: GetBeersFromApiUseCase,
    private val repository: BeersRepository
) : ViewModel() {

    fun getPaginatedBeers(): Flow<PagingData<Beer>> = Pager(PagingConfig(pageSize = 25)) {
        BeersPagingSource(getBeersFromApiUseCase)
    }.flow.cachedIn(viewModelScope)

    @ExperimentalPagingApi
    fun getData(): Flow<PagingData<Beer>> = repository.getPaginatedBeers().cachedIn(viewModelScope)
}
