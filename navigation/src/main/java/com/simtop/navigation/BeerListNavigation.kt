package com.simtop.navigation

import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.dynamicfeatures.DynamicExtras
import com.simtop.beerdomain.domain.models.Beer

interface BeerListNavigation {
    fun fromBeersListToBeerDetail(
        beer: Beer,
        navController: NavController,
        dynamicExtras: DynamicExtras
    )
}