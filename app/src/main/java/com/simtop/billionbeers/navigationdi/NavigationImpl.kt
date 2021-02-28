package com.simtop.billionbeers.navigationdi

import androidx.navigation.fragment.findNavController
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.feature.beerslist.BeersListFragment
import com.simtop.feature.beerslist.BeersListFragmentDirections
import com.simtop.feature.beerslist.navigation.BeerListNavigation
import javax.inject.Inject

class NavigationImpl @Inject constructor(): BeerListNavigation {
   override fun fromBeersListToBeerDetail(beer: Beer, fragment: BeersListFragment) {
        val action = BeersListFragmentDirections.actionBeersListFragmentToBeerDetailFragment(beer)
        fragment.findNavController().navigate(action)
    }
}