package com.simtop.billionbeers.testing.domain

import com.simtop.billionbeers.domain.models.Beer

val fakeBeerModel = Beer("1", "Buzz", "A Real Bitter Experience.", "", "", 0.0, 0.0, emptyList())

val fakeBeerListModel = listOf(fakeBeerModel.copy())

const val fakeErrorName = "Error getting list of beers"

val fakeException = Exception(fakeErrorName)
