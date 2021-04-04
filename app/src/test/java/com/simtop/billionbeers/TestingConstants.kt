package com.simtop.billionbeers

import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beerdomain.domain.models.Beer

const val FAKE_JSON = "fake_json_response.json"

val fakeBeersApiResponseItem = BeersApiResponseItem(
    1,
    "Buzz",
    "A Real Bitter Experience.",
    "",
    "",
    0.0,
    0.0,
    emptyList()
)

val fakeBeerApiResponse = listOf(fakeBeersApiResponseItem.copy())

val fakeBeerModel = Beer(
    1,
    "Buzz",
    "A Real Bitter Experience.",
    "",
    "",
    0.0,
    0.0,
    emptyList()
)

val fakeBeerListModel = listOf(fakeBeerModel.copy())

const val fakeErrorName = "Error getting list of beers"

val fakeException = Exception(fakeErrorName)