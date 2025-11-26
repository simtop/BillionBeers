package com.simtop.beer_network.remotesources

import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.network.BeersService
import javax.inject.Inject

interface BeersRemoteSourceContract {
    suspend fun getListOfBeers(page: Int): List<BeersApiResponseItem>
}

class BeersRemoteSourceImpl @Inject constructor(private val service: BeersService) : BeersRemoteSourceContract {

    override suspend fun getListOfBeers(
        page : Int
    ): List<BeersApiResponseItem> {
        return service.getListOfBeers(page)
    }
}