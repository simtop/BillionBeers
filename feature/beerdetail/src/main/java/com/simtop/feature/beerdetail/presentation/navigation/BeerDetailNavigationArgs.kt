package com.simtop.feature.beerdetail.presentation.navigation

import com.simtop.beerdomain.navigation.BeerDetailNavValues
import com.simtop.feature.beerdetail.presentation.BeerDetailFragment

interface BeerDetailNavigationArgs {
    fun getBeerDetailArgs(fragment: BeerDetailFragment) : BeerDetailNavValues
}