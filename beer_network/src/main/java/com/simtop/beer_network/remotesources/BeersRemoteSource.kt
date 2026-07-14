package com.simtop.beer_network.remotesources

import com.simtop.beer_network.models.BeersPage
import com.simtop.beer_network.network.BeersService
import com.simtop.core.core.LanguageProvider
import dev.zacsweers.metro.Inject

interface BeersRemoteSource {
  /** [search] null fetches the full catalog; non-null adds `q=` for the search surface. */
  suspend fun getListOfBeers(page: Int, search: String? = null): BeersPage
}

class BeersRemoteSourceImpl
@Inject
constructor(private val service: BeersService, private val languageProvider: LanguageProvider) :
  BeersRemoteSource {

  override suspend fun getListOfBeers(page: Int, search: String?): BeersPage {
    val response =
      service.getListOfBeers(
        page = page,
        languageCode = languageProvider.currentLanguageCode(),
        search = search,
      )
    // Malformed or absent header → null; the pager falls back to the empty-page probe.
    val totalCount = response.headers()[HEADER_TOTAL_COUNT]?.toIntOrNull()
    return BeersPage(items = response.body().orEmpty(), totalCount = totalCount)
  }

  private companion object {
    const val HEADER_TOTAL_COUNT = "X-Total-Count"
  }
}
