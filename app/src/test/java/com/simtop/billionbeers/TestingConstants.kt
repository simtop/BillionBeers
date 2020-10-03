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

val fakeBeerModel2 = Beer(
    2,
    "Buzz2",
    "A Real Bitter Experience.2",
    "",
    "",
    0.0,
    0.0,
    emptyList()
)

val fakeBeerListModel = listOf(fakeBeerModel.copy())

val fakeBeerListModel2 = listOf(fakeBeerModel.copy(),fakeBeerModel2.copy())

val fakePagingBeer = PagingData.from(fakeBeerListModel)
val fakeFlowPagingBeer = flowOf(fakePagingBeer)
