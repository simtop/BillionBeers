package com.simtop.beer_network.remotesources

import com.simtop.beer_network.TestMockWebService
import com.simtop.beer_network.fixtures.FAKE_BREWERIES_JSON
import com.simtop.beer_network.fixtures.FAKE_JSON
import com.simtop.beer_network.fixtures.FAKE_TYPOLOGIES_JSON
import com.simtop.beer_network.network.BeersServiceHttpException
import com.simtop.core.core.LanguageProvider
import java.net.HttpURLConnection
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import okhttp3.mockwebserver.MockResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BeersRemoteSourceTest : TestMockWebService() {

  private val remoteSource by lazy { BeersRemoteSourceImpl(apiService, LanguageProvider { "en" }) }

  @Test
  fun `catalog request uses the documented defaults and omits null filters`() {
    mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_OK)

    runBlocking { remoteSource.getListOfBeers(page = 3) }

    val request = mockServer.takeRequest()
    assertEquals("GET", request.method)
    assertEquals("/beers?_page=3&_limit=25&translations.language.code=en", request.path)
  }

  @Test
  fun `filtered request encodes values and sends all filters`() {
    mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_OK)

    runBlocking {
      remoteSource.getListOfBeers(
        page = 2,
        search = "ipa & stout",
        typologyId = "style/1",
        breweryId = "brewery 1",
      )
    }

    val path = mockServer.takeRequest().path.orEmpty()
    assertEquals(
      "/beers?_page=2&_limit=25&translations.language.code=en" +
        "&q=ipa%20%26%20stout&typology.id=style%2F1&brewery.id=brewery%201",
      path,
    )
  }

  @Test
  fun `malformed body is not treated as a successful empty page`() {
    mockServer.enqueue(
      MockResponse()
        .setResponseCode(HttpURLConnection.HTTP_OK)
        .setHeader("Content-Type", "application/json")
        .setBody("not-json"),
    )

    assertFailsWith<SerializationException> {
      runBlocking { remoteSource.getListOfBeers(1) }
    }
  }

  @Test
  fun `returns the parsed body and total count from the X-Total-Count header`() {
    mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_OK, mapOf("X-Total-Count" to "206"))

    val page = runBlocking { remoteSource.getListOfBeers(1) }

    assertEquals(listOf("1"), page.items.map { it.id })
    assertEquals(206, page.totalCount)
  }

  @Test
  fun `total count is null when the header is absent`() {
    mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_OK)

    val page = runBlocking { remoteSource.getListOfBeers(1) }

    assertEquals(null, page.totalCount)
  }

  @Test
  fun `total count is null when the header is malformed`() {
    mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_OK, mapOf("X-Total-Count" to "not-a-number"))

    val page = runBlocking { remoteSource.getListOfBeers(1) }

    assertEquals(null, page.totalCount)
  }

  @Test
  fun `throws the neutral HTTP failure carrying the status code when the service fails`() {
    mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_UNAVAILABLE)

    val thrown =
      assertFailsWith<BeersServiceHttpException> {
        runBlocking { remoteSource.getListOfBeers(1) }
      }

    assertEquals(HttpURLConnection.HTTP_UNAVAILABLE, thrown.statusCode)
  }

  @Test
  fun `a server error never surfaces as an empty page`() {
    mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_INTERNAL_ERROR)

    assertFailsWith<BeersServiceHttpException> {
      runBlocking { remoteSource.getListOfBeers(1) }
    }
  }

  @Test
  fun `a search sends the q query parameter`() {
    mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_OK, mapOf("X-Total-Count" to "159"))

    runBlocking { remoteSource.getListOfBeers(1, search = "ipa") }

    val path = mockServer.takeRequest().path.orEmpty()
    assertTrue(path.contains("q=ipa"))
  }

  @Test
  fun `a catalog fetch omits the q query parameter`() {
    mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_OK)

    runBlocking { remoteSource.getListOfBeers(1) }

    val path = mockServer.takeRequest().path.orEmpty()
    assertTrue(!path.contains("q="))
  }

  @Test
  fun `a style filter sends the typology id query parameter`() {
    mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_OK, mapOf("X-Total-Count" to "9"))

    runBlocking { remoteSource.getListOfBeers(1, typologyId = "t1") }

    val path = mockServer.takeRequest().path.orEmpty()
    assertTrue(path.contains("typology.id=t1"))
  }

  @Test
  fun `a brewery filter sends the brewery id query parameter`() {
    mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_OK, mapOf("X-Total-Count" to "5"))

    runBlocking { remoteSource.getListOfBeers(1, breweryId = "b1") }

    val path = mockServer.takeRequest().path.orEmpty()
    assertTrue(path.contains("brewery.id=b1"))
  }

  @Test
  fun `a catalog fetch omits the filter query parameters`() {
    mockHttpResponse(FAKE_JSON, HttpURLConnection.HTTP_OK)

    runBlocking { remoteSource.getListOfBeers(1) }

    val path = mockServer.takeRequest().path.orEmpty()
    assertTrue(!path.contains("typology.id="))
    assertTrue(!path.contains("brewery.id="))
  }

  @Test
  fun `typologies parse ignoring the unrendered translations`() {
    mockHttpResponse(FAKE_TYPOLOGIES_JSON, HttpURLConnection.HTTP_OK)

    val typologies = runBlocking { remoteSource.getTypologies() }

    assertEquals(listOf("t1", "t2"), typologies.map { it.id })
    assertEquals(listOf("IPA (Indian Pale Ale)", "Stout"), typologies.map { it.name })
  }

  @Test
  fun `breweries parse the embedded country and image`() {
    mockHttpResponse(FAKE_BREWERIES_JSON, HttpURLConnection.HTTP_OK)

    val breweries = runBlocking { remoteSource.getBreweries() }

    assertEquals(1, breweries.size)
    assertEquals("b1", breweries[0].id)
    assertEquals("Supreme Suds Collective", breweries[0].name)
    assertEquals(1972, breweries[0].foundedYear)
    assertEquals("KP", breweries[0].country?.code)
    assertEquals("https://brewbuddy.dev/images/b1.jpg", breweries[0].image?.url)
  }
}
