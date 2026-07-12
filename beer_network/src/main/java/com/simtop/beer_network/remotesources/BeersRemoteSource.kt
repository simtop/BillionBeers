package com.simtop.beer_network.remotesources

import com.simtop.beer_network.models.BeersPage
import com.simtop.beer_network.network.BeersService
import com.simtop.core.core.LanguageProvider
import dev.zacsweers.metro.Inject

interface BeersRemoteSource {
  suspend fun getListOfBeers(page: Int): BeersPage
}

class BeersRemoteSourceImpl
@Inject
constructor(private val service: BeersService, private val languageProvider: LanguageProvider) :
  BeersRemoteSource {

  override suspend fun getListOfBeers(page: Int): BeersPage {
    val response =
      service.getListOfBeers(page = page, languageCode = languageProvider.currentLanguageCode())
    // Malformed or absent header → null; the pager falls back to the empty-page probe.
    val totalCount = response.headers()[HEADER_TOTAL_COUNT]?.toIntOrNull()
    return BeersPage(items = response.body().orEmpty(), totalCount = totalCount)
  }

  private companion object {
    const val HEADER_TOTAL_COUNT = "X-Total-Count"
  }
}
