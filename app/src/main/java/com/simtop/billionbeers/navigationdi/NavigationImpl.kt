package com.simtop.billionbeers.navigationdi

import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.dynamicfeatures.DynamicExtras
import androidx.navigation.fragment.findNavController
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.feature.beerslist.BeersListFragment
import com.simtop.feature.beerslist.BeersListFragmentDirections
import com.simtop.navigation.BeerListNavigation
import javax.inject.Inject


class NavigationImpl @Inject constructor() : BeerListNavigation {
    override fun fromBeersListToBeerDetail(
        beer: Beer,
        navController: NavController,
        dynamicExtras: DynamicExtras
    ) {
        val action =
            BeersListFragmentDirections.actionBeersListFragmentToBeerDetailFragment(beer)
        navController.navigate(action, dynamicExtras)
    }
}