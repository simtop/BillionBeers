package com.simtop.beerdomain.fakes

import com.simtop.beerdomain.domain.models.Beer

val fakeBeerModel = Beer("1", "Buzz", "A Real Bitter Experience.", "", "", 0.0, 0.0, emptyList())

val fakeBeerListModel = listOf(fakeBeerModel.copy())

const val fakeErrorName = "Error getting list of beers"

val fakeException = Exception(fakeErrorName)
