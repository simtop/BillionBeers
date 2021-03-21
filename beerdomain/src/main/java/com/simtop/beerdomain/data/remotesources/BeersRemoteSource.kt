package com.simtop.beerdomain.data.remotesources

import com.simtop.beerdomain.data.models.BeersApiResponseItem
import com.simtop.beerdomain.data.network.BeersService
import javax.inject.Inject

class BeersRemoteSource @Inject constructor(private val service: BeersService) {

    suspend fun getListOfBeers(
        page : Int
    ): List<BeersApiResponseItem> {
        return service.getListOfBeers(page)
    }
}