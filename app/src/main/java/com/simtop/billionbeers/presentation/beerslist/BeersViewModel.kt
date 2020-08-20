package com.simtop.billionbeers.presentation.beerslist

import androidx.lifecycle.ViewModel
import com.simtop.billionbeers.domain.usecases.GetAllBeersUseCase
import javax.inject.Inject

class BeersViewModel @Inject constructor(
    private val getAllBeersUseCase: GetAllBeersUseCase
) : ViewModel() {

}