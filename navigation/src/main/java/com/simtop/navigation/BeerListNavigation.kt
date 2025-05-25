package com.simtop.navigation

import androidx.annotation.Keep
import androidx.navigation.NavController
import androidx.navigation.dynamicfeatures.DynamicExtras
import com.simtop.beerdomain.domain.models.Beer

//@Keep
interface BeerListNavigation {
    fun fromBeersListToBeerDetail(
        beer: Beer,
        navController: NavController,
        dynamicExtras: DynamicExtras
    )
}