package com.simtop.billionbeers

import com.simtop.billionbeers.data.models.BeersApiResponseItem

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