package com.simtop.beer_data.fakes

import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.remotesources.BeersRemoteSource

class FakeBeersRemoteSource : BeersRemoteSource {

    private var beersResponse: List<BeersApiResponseItem> = emptyList()
    private var shouldThrowError = false
    private var exceptionToThrow: Exception = Exception("Fake Remote Error")

    fun setBeersResponse(beers: List<BeersApiResponseItem>) {
        beersResponse = beers
    }

    fun setShouldThrowError(shouldThrow: Boolean, exception: Exception = Exception("Fake Remote Error")) {
        shouldThrowError = shouldThrow
        exceptionToThrow = exception
    }

    override suspend fun getListOfBeers(page: Int): List<BeersApiResponseItem> {
        if (shouldThrowError) {
            throw exceptionToThrow
        }
        return beersResponse
    }
}
