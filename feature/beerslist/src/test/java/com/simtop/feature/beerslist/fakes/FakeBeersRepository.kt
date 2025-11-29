package com.simtop.feature.beerslist.fakes

import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.PagingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeBeersRepository : BeersRepository {

    private val beersFlow = MutableStateFlow<List<Beer>>(emptyList())
    private val pagingStateFlow = MutableStateFlow<PagingState>(PagingState.Idle)

    // Helper to set state from tests
    fun setBeers(beers: List<Beer>) {
        beersFlow.value = beers
    }

    fun setPagingState(state: PagingState) {
        pagingStateFlow.value = state
    }

    override suspend fun countDBEntries(): Int {
        return beersFlow.value.size
    }

    override suspend fun getAllBeersFromDB(): List<Beer> {
        return beersFlow.value
    }

    override suspend fun insertAllToDB(beers: List<Beer>) {
        beersFlow.value = beersFlow.value + beers
    }

    override suspend fun updateAvailability(beer: Beer) {
        val currentList = beersFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == beer.id }
        if (index != -1) {
            currentList[index] = beer
            beersFlow.value = currentList
        }
    }

    override fun getBeersFromSingleSource(quantity: Int): Flow<List<Beer>> {
        return beersFlow.map { it.take(quantity) }
    }

    override fun observePagingState(): Flow<PagingState> {
        return pagingStateFlow
    }

    override suspend fun loadNextPage() {
        pagingStateFlow.value = PagingState.LoadingNextPage
        // Simulate network delay if needed, or just transition to success
        // For now, let's assume success after "loading"
        pagingStateFlow.value = PagingState.Success
    }

    override suspend fun getListOfBeerFromApi(page: Int): List<Beer> {
        // Return empty or pre-configured list
        return emptyList()
    }

    override suspend fun getQuantityOfBeerFromApi(quantity: Int): List<Beer> {
        return emptyList()
    }
}
