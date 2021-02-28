package com.simtop.billionbeers.navigationdi

import com.simtop.feature.beerdetail.presentation.navigation.BeerDetailNavigationArgs
import com.simtop.feature.beerslist.navigation.BeerListNavigation
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent

@Module
@InstallIn(ApplicationComponent::class)
abstract class NavigationModule {
    @Binds
    abstract fun getNavigator(navigator: NavigationImpl): BeerListNavigation

    @Binds
    abstract fun getArgs(args: ArgsImpl): BeerDetailNavigationArgs
}