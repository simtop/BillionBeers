package com.simtop.billionbeers.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.simtop.billionbeers.presentation.beerslist.BeersViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

@Module
abstract class ViewModelsModule {

    @Binds
    abstract fun bindViewModelFactory(factory: ViewModelFactory): ViewModelProvider.Factory

    @Binds
    @IntoMap
    @ViewModelKey(BeersViewModel::class)
    abstract fun bindBaseViewModel(viewModel: BeersViewModel): ViewModel

}