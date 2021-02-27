package com.simtop.billionbeers

import com.simtop.beerdomain.data.models.BeersApiResponseItem
import com.simtop.beerdomain.domain.models.Beer

const val FAKE_JSON = "fake_json_response.json"

val fakeBeersApiResponseItem = com.simtop.beerdomain.data.models.BeersApiResponseItem(
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

val fakeBeerModel = com.simtop.beerdomain.domain.models.Beer(
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

val fakeErrorName = "Error getting list of beers"

val fakeException = Exception(fakeErrorName)