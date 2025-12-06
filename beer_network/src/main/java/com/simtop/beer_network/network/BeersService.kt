package com.simtop.beer_network.network

import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.ImageResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface BeersService {
  @GET("beers")
  suspend fun getListOfBeers(
    @Query("_page") page: Int,
    @Query("_limit") perPage: Int = DEFAULT_ITEMS_PER_PAGE
  ): List<BeersApiResponseItem>

  @GET("images/{id}") suspend fun getImage(@Path("id") id: String): ImageResponse

  companion object {
    const val DEFAULT_ITEMS_PER_PAGE = 25
  }
}
