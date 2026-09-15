package com.simtop.beer_network.remotesources

import com.simtop.beer_network.models.BeersPage
import com.simtop.beer_network.models.BreweryApiResponseItem
import com.simtop.beer_network.models.TypologyApiResponseItem
import com.simtop.beer_network.network.BeersService
import com.simtop.beer_network.network.BeersServiceHttpException
import com.simtop.core.core.LanguageProvider
import dev.zacsweers.metro.Inject

interface BeersRemoteSource {
  /**
   * One page of beers. All filters null fetches the full catalog; [search] adds `q=`, [typologyId]
   * `typology.id=`, [breweryId] `brewery.id=` - the server ANDs whichever are present.
   */
  suspend fun getListOfBeers(
    page: Int,
    search: String? = null,
    typologyId: String? = null,
    breweryId: String? = null,
  ): BeersPage

  /** All beer styles - 17 rows, unpaged. */
  suspend fun getTypologies(): List<TypologyApiResponseItem>

  /** All breweries - 38 rows, unpaged. */
  suspend fun getBreweries(): List<BreweryApiResponseItem>
}

@Inject
class BeersRemoteSourceImpl(
  private val service: BeersService,
  private val languageProvider: LanguageProvider,
) :
  BeersRemoteSource {

  override suspend fun getListOfBeers(
    page: Int,
    search: String?,
    typologyId: String?,
    breweryId: String?,
  ): BeersPage {
    val response =
      service.getListOfBeers(
        page = page,
        languageCode = languageProvider.currentLanguageCode(),
        search = search,
        typologyId = typologyId,
        breweryId = breweryId,
      )
    checkSuccessful(response.statusCode)
    // Malformed or absent header → null; the pager falls back to the empty-page probe.
    val totalCount = response.header(HEADER_TOTAL_COUNT)?.toIntOrNull()
    return BeersPage(items = response.body.orEmpty(), totalCount = totalCount)
  }

  override suspend fun getTypologies(): List<TypologyApiResponseItem> {
    val response = service.getTypologies()
    checkSuccessful(response.statusCode)
    return response.body.orEmpty()
  }

  override suspend fun getBreweries(): List<BreweryApiResponseItem> {
    val response = service.getBreweries()
    checkSuccessful(response.statusCode)
    return response.body.orEmpty()
  }

  private fun checkSuccessful(statusCode: Int) {
    if (statusCode !in HTTP_SUCCESS_RANGE) throw BeersServiceHttpException(statusCode)
  }

  private companion object {
    const val HEADER_TOTAL_COUNT = "X-Total-Count"
    val HTTP_SUCCESS_RANGE = 200..299
  }
}
