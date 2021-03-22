package com.simtop.beer_network.remotesources

import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.network.BeersService
import javax.inject.Inject

class BeersRemoteSource @Inject constructor(private val service: BeersService) {

    suspend fun getListOfBeers(
        page : Int
    ): List<BeersApiResponseItem> {
        return service.getListOfBeers(page)
    }
}