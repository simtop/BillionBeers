package com.simtop.billionbeers.navigationdi

import com.simtop.feature.beerdetail.presentation.navigation.BeerDetailNavigation
import com.simtop.feature.beerdetail.presentation.navigation.BeerDetailNavigationArgs
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

    @Binds
    abstract fun getDetailNavigator(navigator: NavigationImpl): BeerDetailNavigation

    @Binds
    abstract fun getArgs(args: ArgsImpl): BeerDetailNavigationArgs
}