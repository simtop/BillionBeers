package com.simtop.billionbeers.navigationdi


import com.simtop.feature.beerslist.navigation.BeerListNavigation
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationModule {
    @Binds
    abstract fun getListNavigator(navigator: NavigationImpl): BeerListNavigation
}