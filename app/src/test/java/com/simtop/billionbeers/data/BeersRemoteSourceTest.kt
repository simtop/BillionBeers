package com.simtop.billionbeers.data

import com.simtop.beer_network.fixtures.FAKE_JSON
import com.simtop.beer_network.remotesources.BeersRemoteSourceImpl
import com.simtop.billionbeers.TestMockWebService
import com.simtop.core.core.LanguageProvider
import java.net.HttpURLConnection
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

class BeersRemoteSourceTest : TestMockWebService() {

  private val remoteSource by lazy { BeersRemoteSourceImpl(apiService, LanguageProvider { "en" }) }

  @Test
  fun `returns the parsed body and total count from the X-Total-Count header`() {
    mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_OK, mapOf("X-Total-Count" to "206"))

    val page = runBlocking { remoteSource.getListOfBeers(1) }

    page.items.map { it.id } shouldBeEqualTo listOf("1")
    page.totalCount shouldBeEqualTo 206
  }

  @Test
  fun `total count is null when the header is absent`() {
    mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_OK)

    val page = runBlocking { remoteSource.getListOfBeers(1) }

    page.totalCount shouldBeEqualTo null
  }

  @Test
  fun `total count is null when the header is malformed`() {
    mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_OK, mapOf("X-Total-Count" to "not-a-number"))

    val page = runBlocking { remoteSource.getListOfBeers(1) }

    page.totalCount shouldBeEqualTo null
  }

  @Test(expected = Exception::class)
  fun `throws when the service fails`() {
    mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_UNAVAILABLE)

    runBlocking { remoteSource.getListOfBeers(1) }
  }

  @Test
  fun `a search sends the q query parameter`() {
    mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_OK, mapOf("X-Total-Count" to "159"))

    runBlocking { remoteSource.getListOfBeers(1, search = "ipa") }

    val path = mockServer.takeRequest().path.orEmpty()
    path.contains("q=ipa") shouldBeEqualTo true
  }

  @Test
  fun `a catalog fetch omits the q query parameter`() {
    mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_OK)

    runBlocking { remoteSource.getListOfBeers(1) }

    val path = mockServer.takeRequest().path.orEmpty()
    path.contains("q=") shouldBeEqualTo false
  }
}
