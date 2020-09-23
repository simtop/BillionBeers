package com.simtop.billionbeers

import androidx.paging.PagingData
import com.simtop.billionbeers.data.models.BeersApiResponseItem
import com.simtop.billionbeers.domain.models.Beer
import kotlinx.coroutines.flow.flowOf

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

val fakePagingBeer = PagingData.from(fakeBeerListModel)
val fakeFlowPagingBeer = flowOf(fakePagingBeer)
