package com.simtop.beer_data.fakes

import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.BeersPage
import com.simtop.beer_network.remotesources.BeersRemoteSource

class FakeBeersRemoteSource : BeersRemoteSource {

  private var beersResponse: List<BeersApiResponseItem> = emptyList()
  private var totalCount: Int? = null
  private var shouldThrowError = false
  private var exceptionToThrow: Exception = Exception("Fake Remote Error")

  val requestedPages = mutableListOf<Int>()

  fun setBeersResponse(beers: List<BeersApiResponseItem>, totalCount: Int? = null) {
    beersResponse = beers
    this.totalCount = totalCount
  }

  fun setShouldThrowError(
    shouldThrow: Boolean,
    exception: Exception = Exception("Fake Remote Error"),
  ) {
    shouldThrowError = shouldThrow
    exceptionToThrow = exception
  }

  override suspend fun getListOfBeers(page: Int): BeersPage {
    requestedPages += page
    if (shouldThrowError) {
      throw exceptionToThrow
    }
    return BeersPage(items = beersResponse, totalCount = totalCount)
  }
}
