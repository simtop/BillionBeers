package com.simtop.beer_network.fixtures

import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.Language
import com.simtop.beer_network.models.Translation

const val FAKE_JSON = "fake_json_response.json"

val fakeBeersApiResponseItem =
  BeersApiResponseItem(
    "1",
    "Buzz",
    0.0,
    0.0,
    "",
    translations = listOf(Translation(Language("en"), "A Real Bitter Experience.", "")),
    emptyList(),
  )

val fakeBeerApiResponse = listOf(fakeBeersApiResponseItem.copy())
