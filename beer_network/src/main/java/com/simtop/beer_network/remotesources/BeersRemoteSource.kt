package com.simtop.beer_network.remotesources

import com.simtop.beer_network.models.BeersPage
import com.simtop.beer_network.models.BreweryApiResponseItem
import com.simtop.beer_network.models.TypologyApiResponseItem
import com.simtop.beer_network.network.BeersService
import com.simtop.core.core.LanguageProvider
import dev.zacsweers.metro.Inject
import retrofit2.HttpException

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

class BeersRemoteSourceImpl
@Inject
constructor(private val service: BeersService, private val languageProvider: LanguageProvider) :
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
    // Response<> means Retrofit hands back non-2xx instead of throwing, and a failed body is null.
    // Without this guard a 5xx would become an empty page - which PagingMediator's empty-page probe
    // reads as end-of-pagination, silently truncating the list with no error and no retry. Throwing
    // matches the unpaged endpoints (no Response<> wrapper, so Retrofit throws) and lands in
    // toFetchBeersError() like any other HTTP failure.
    if (!response.isSuccessful) throw HttpException(response)
    // Malformed or absent header → null; the pager falls back to the empty-page probe.
    val totalCount = response.headers()[HEADER_TOTAL_COUNT]?.toIntOrNull()
    return BeersPage(items = response.body().orEmpty(), totalCount = totalCount)
  }

  override suspend fun getTypologies(): List<TypologyApiResponseItem> = service.getTypologies()

  override suspend fun getBreweries(): List<BreweryApiResponseItem> = service.getBreweries()

  private companion object {
    const val HEADER_TOTAL_COUNT = "X-Total-Count"
  }
}
