package com.simtop.beer_network.remotesources

import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.network.BeersService
import com.simtop.core.core.LanguageProvider
import dev.zacsweers.metro.Inject

interface BeersRemoteSource {
  suspend fun getListOfBeers(page: Int): List<BeersApiResponseItem>

  suspend fun getImage(id: String): com.simtop.beer_network.models.ImageResponse
}

class BeersRemoteSourceImpl
@Inject
constructor(private val service: BeersService, private val languageProvider: LanguageProvider) :
  BeersRemoteSource {

  override suspend fun getListOfBeers(page: Int): List<BeersApiResponseItem> {
    return service.getListOfBeers(
      page = page,
      languageCode = languageProvider.currentLanguageCode(),
    )
  }

  override suspend fun getImage(id: String): com.simtop.beer_network.models.ImageResponse {
    return service.getImage(id)
  }
}
