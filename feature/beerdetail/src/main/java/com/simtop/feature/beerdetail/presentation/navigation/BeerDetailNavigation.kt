package com.simtop.feature.beerdetail.presentation.navigation

import com.simtop.feature.beerdetail.presentation.BeerDetailFragment

interface BeerDetailNavigation {
    fun fromBeersListToBeerDetail(fragment: BeerDetailFragment)
}