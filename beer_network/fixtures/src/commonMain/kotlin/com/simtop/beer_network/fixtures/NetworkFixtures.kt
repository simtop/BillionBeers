package com.simtop.beer_network.fixtures

import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.Language
import com.simtop.beer_network.models.Translation

const val FAKE_JSON = "fake_json_response.json"

const val FAKE_TYPOLOGIES_JSON = "fake_typologies_response.json"

const val FAKE_BREWERIES_JSON = "fake_breweries_response.json"

val fakeBeersApiResponseItem =
  BeersApiResponseItem(
    id = "1",
    name = "Buzz",
    abv = 0.0,
    ibu = 0.0,
    translations = listOf(Translation(Language("en"), "A Real Bitter Experience.", "")),
    foodPairing = emptyList(),
  )

val fakeBeerApiResponse = listOf(fakeBeersApiResponseItem.copy())
