package com.simtop.billionbeers.navigationdi

import androidx.navigation.fragment.navArgs
import com.simtop.beerdomain.navigation.BeerDetailNavValues
import com.simtop.feature.beerdetail.presentation.BeerDetailFragment
import com.simtop.feature.beerdetail.presentation.BeerDetailFragmentArgs
import com.simtop.feature.beerdetail.presentation.navigation.BeerDetailNavigationArgs
import javax.inject.Inject


class ArgsImpl @Inject constructor(): BeerDetailNavigationArgs {
    override fun getBeerDetailArgs(fragment: BeerDetailFragment) : BeerDetailNavValues {
        val args: BeerDetailFragmentArgs by fragment.navArgs()
        return BeerDetailNavValues(args.myArg)
    }
}