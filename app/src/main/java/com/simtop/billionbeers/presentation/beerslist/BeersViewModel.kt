package com.simtop.billionbeers.presentation.beerslist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.billionbeers.core.Either
import com.simtop.billionbeers.core.MAX_PAGES_FOR_PAGINATION
import com.simtop.billionbeers.core.ViewState
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.domain.usecases.GetAllBeersUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class BeersViewModel @Inject constructor(
    private val getAllBeersUseCase: GetAllBeersUseCase
) : ViewModel() {
    private val _myViewState =
        MutableLiveData<ViewState<Exception, List<Beer>>>(ViewState.Loading)
    val myViewState: LiveData<ViewState<Exception, List<Beer>>>
        get() = _myViewState

    lateinit var detailBeer: Beer

    fun getAllBeers(quantity : Int = MAX_PAGES_FOR_PAGINATION) {
        viewModelScope.launch(Dispatchers.IO) {
            getAllBeersUseCase.execute(getAllBeersUseCase.Params(quantity))
                .also(::process)
        }
    }

    private fun process(either: Either<Exception, List<Beer>>) {
        _myViewState.postValue(ViewState.Result(either))
    }

    fun saveBeerDetail(beer: Beer) {
        detailBeer = beer
    }
}