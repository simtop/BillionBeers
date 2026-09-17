package com.simtop.beer_network.network

import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.BreweryApiResponseItem
import com.simtop.beer_network.models.TypologyApiResponseItem

/** Transport-neutral response metadata retained for pagination and HTTP error handling. */
data class BeersServiceResponse<T>(
  val body: T?,
  val statusCode: Int,
  val headers: Map<String, String>,
) {
  val isSuccessful: Boolean
    get() = statusCode in 200..299

  fun header(name: String): String? =
    headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

/** HTTP failure raised by a service adapter when the server returns a non-2xx response. */
class BeersServiceHttpException(val statusCode: Int) : RuntimeException("HTTP $statusCode")

/** Transport failure raised when a service adapter cannot obtain an HTTP response. */
class BeersServiceNetworkException(cause: Throwable? = null) : RuntimeException(cause)

interface BeersService {
  suspend fun getListOfBeers(
    page: Int,
    perPage: Int = DEFAULT_ITEMS_PER_PAGE,
    languageCode: String = DEFAULT_LANGUAGE_CODE,
    search: String? = null,
    typologyId: String? = null,
    breweryId: String? = null,
  ): BeersServiceResponse<List<BeersApiResponseItem>>

  suspend fun getTypologies(): BeersServiceResponse<List<TypologyApiResponseItem>>

  suspend fun getBreweries(): BeersServiceResponse<List<BreweryApiResponseItem>>

  companion object {
    const val DEFAULT_ITEMS_PER_PAGE = 25
    const val DEFAULT_LANGUAGE_CODE = "en"
  }
}
