package com.simtop.billionbeers.navigationdi

import androidx.navigation.dynamicfeatures.DynamicExtras
import androidx.navigation.dynamicfeatures.DynamicInstallMonitor
import androidx.navigation.fragment.findNavController
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.feature.beerslist.BeersListFragment
import com.simtop.feature.beerslist.BeersListFragmentDirections
import com.simtop.feature.beerslist.navigation.BeerListNavigation
import javax.inject.Inject


class NavigationImpl @Inject constructor(): BeerListNavigation  {//, BeerDetailNavigation {
   override fun fromBeersListToBeerDetail(beer: Beer, fragment: BeersListFragment, installMonitor: DynamicInstallMonitor) {

    //val installMonitor2 = DynamicInstallMonitor()
       val action = BeersListFragmentDirections.actionBeersListFragmentToBeerDetailFragment(beer)
       fragment.findNavController().navigate(action, DynamicExtras(installMonitor))
    }

//    override fun fromBeersListToBeerDetail(fragment: BeerDetailFragment) {
//        fragment.findNavController().popBackStack()
//    }
}