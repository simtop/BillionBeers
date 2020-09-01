package com.simtop.billionbeers.data

import com.simtop.billionbeers.data.mappers.BeersMapper
import com.simtop.billionbeers.data.models.BeersApiResponseItem
import com.simtop.billionbeers.domain.models.Beer

class BeersLocalSourceTest {

}

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

val fakeDBList = fakeBeerListModel.map {
    BeersMapper.fromBeerToBeerDbModel(it) }

val fakeDB = BeersMapper.fromBeerToBeerDbModel(fakeBeerModel)