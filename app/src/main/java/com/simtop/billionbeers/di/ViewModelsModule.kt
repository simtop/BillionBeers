package com.simtop.billionbeers.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.simtop.billionbeers.presentation.beerdetail.BeerDetailViewModel
import com.simtop.billionbeers.presentation.beerslist.BeersListViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

@Module
abstract class ViewModelsModule {

    @Binds
    abstract fun bindViewModelFactory(factory: ViewModelFactory): ViewModelProvider.Factory

    @Binds
    @IntoMap
    @ViewModelKey(BeersListViewModel::class)
    abstract fun bindBeersListViewModel(viewModel: BeersListViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(BeerDetailViewModel::class)
    abstract fun bindBeerDetailViewModel(viewModel: BeerDetailViewModel): ViewModel

}