package com.simtop.beer_data.fakes

import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.BeersPage
import com.simtop.beer_network.models.BreweryApiResponseItem
import com.simtop.beer_network.models.TypologyApiResponseItem
import com.simtop.beer_network.remotesources.BeersRemoteSource

class FakeBeersRemoteSource : BeersRemoteSource {

  private var beersResponse: List<BeersApiResponseItem> = emptyList()
  private var totalCount: Int? = null
  private var shouldThrowError = false
  private var exceptionToThrow: Exception = Exception("Fake Remote Error")

  val requestedPages = mutableListOf<Int>()
  val requestedSearches = mutableListOf<String?>()
  val requestedTypologyIds = mutableListOf<String?>()
  val requestedBreweryIds = mutableListOf<String?>()

  var typologiesResponse: List<TypologyApiResponseItem> = emptyList()
  var breweriesResponse: List<BreweryApiResponseItem> = emptyList()

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

  override suspend fun getListOfBeers(
    page: Int,
    search: String?,
    typologyId: String?,
    breweryId: String?,
  ): BeersPage {
    requestedPages += page
    requestedSearches += search
    requestedTypologyIds += typologyId
    requestedBreweryIds += breweryId
    if (shouldThrowError) {
      throw exceptionToThrow
    }
    return BeersPage(items = beersResponse, totalCount = totalCount)
  }

  override suspend fun getTypologies(): List<TypologyApiResponseItem> {
    if (shouldThrowError) throw exceptionToThrow
    return typologiesResponse
  }

  override suspend fun getBreweries(): List<BreweryApiResponseItem> {
    if (shouldThrowError) throw exceptionToThrow
    return breweriesResponse
  }
}
