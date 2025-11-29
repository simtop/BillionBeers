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

    override suspend fun getImage(id: String): com.simtop.beer_network.models.ImageResponse {
        return com.simtop.beer_network.models.ImageResponse(
            id = id,
            url = "https://fake.url/image.jpg",
            filename = "image.jpg",
            extension = "jpg",
            width = 100,
            height = 100,
            mime = "image/jpeg",
            size = 100
        )
    }
}
