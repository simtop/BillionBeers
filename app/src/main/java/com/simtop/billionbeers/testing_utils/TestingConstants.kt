package com.simtop.billionbeers.testing_utils

import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.Language
import com.simtop.beer_network.models.Translation
import com.simtop.beerdomain.domain.models.Beer

const val FAKE_JSON = "fake_json_response.json"

val fakeBeersApiResponseItem = BeersApiResponseItem(
    "1",
    "Buzz",
    0.0,
    0.0,
    "",
    translations = listOf(
        Translation(
            Language("en"),
            "A Real Bitter Experience.",
            ""
        )
    ),
    emptyList()
)

val fakeBeerApiResponse = listOf(fakeBeersApiResponseItem.copy())

val fakeBeerModel = Beer(
    "1",
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
