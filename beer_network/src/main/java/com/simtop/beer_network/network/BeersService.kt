package com.simtop.beer_network.network

import com.simtop.beer_network.models.BeersApiResponseItem
import retrofit2.http.GET
import retrofit2.http.Query

interface BeersService {
    @GET("beers")
    suspend fun getListOfBeers(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = DEFAULT_ITEMS_PER_PAGE
    ): List<BeersApiResponseItem>

    companion object {
        const val DEFAULT_ITEMS_PER_PAGE = 25
    }
}
