package com.simtop.billionbeers.data.remotesources

import com.simtop.billionbeers.data.models.BeersApiResponseItem
import com.simtop.billionbeers.data.network.BeersService
import javax.inject.Inject

class BeersRemoteSource @Inject constructor(private val service: BeersService) {

    suspend fun getListOfBeers(
        page : Int
    ): List<BeersApiResponseItem> {
        return service.getListOfBeers(page)
    }
}