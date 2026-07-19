package com.simtop.beer_network.network

import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.BreweryApiResponseItem
import com.simtop.beer_network.models.TypologyApiResponseItem
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface BeersService {
  // Returns Response<…> so the remote source can read the X-Total-Count header, not just the body.
  @GET("beers")
  suspend fun getListOfBeers(
    @Query("_page") page: Int,
    @Query("_limit") perPage: Int = DEFAULT_ITEMS_PER_PAGE,
    @Query("translations.language.code") languageCode: String = DEFAULT_LANGUAGE_CODE,
    // Filters; null omits the param entirely, so the catalog's unfiltered fetch stays byte-
    // identical on the wire. The server ANDs whichever are present.
    @Query("q") search: String? = null,
    @Query("typology.id") typologyId: String? = null,
    @Query("brewery.id") breweryId: String? = null,
  ): Response<List<BeersApiResponseItem>>

  // 17 rows - deliberately unpaged, so no Response<> wrapper: there's no header worth reading.
  @GET("typologies") suspend fun getTypologies(): List<TypologyApiResponseItem>

  // 38 rows - same reasoning.
  @GET("breweries") suspend fun getBreweries(): List<BreweryApiResponseItem>

  companion object {
    const val DEFAULT_ITEMS_PER_PAGE = 25
    // Fallback when LanguageProvider can't resolve a device language, and the language BeersMapper
    // falls back to if the API doesn't have a translation for the requested one.
    const val DEFAULT_LANGUAGE_CODE = "en"
  }
}
