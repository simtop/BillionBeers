package com.simtop.feature.beerslist.navigation

import androidx.navigation.dynamicfeatures.DynamicInstallMonitor
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.feature.beerslist.BeersListFragment

interface BeerListNavigation {
    fun fromBeersListToBeerDetail(beer: Beer, fragment: BeersListFragment, installMonitor: DynamicInstallMonitor)
}