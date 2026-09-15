package com.simtop.beer_network.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KtorBeersServiceTest {

  @Test
  fun `request preserves documented defaults and percent encodes filters`() = runTest {
    var requestPath = ""
    val client = mockClient { request ->
      requestPath = request.url.encodedPath + "?" + request.url.encodedQuery
      respond(
        content = "[]",
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }

    KtorBeersService(client).getListOfBeers(
      page = 2,
      search = "ipa & stout",
      typologyId = "style/1",
      breweryId = "brewery 1",
    )

    assertEquals(
      "/beers?_page=2&_limit=25&translations.language.code=en" +
        "&q=ipa%20%26%20stout&typology.id=style%2F1&brewery.id=brewery%201",
      requestPath,
    )
    client.close()
  }

  @Test
  fun `successful response exposes body status and headers`() = runTest {
    val client = mockClient {
      respond(
        content = "[{\"id\":\"1\",\"name\":\"Buzz\",\"abv\":0.0,\"ibu\":0.0," +
          "\"translations\":[],\"foodPairing\":[]}]",
        status = HttpStatusCode.OK,
        headers =
          headersOf(
            HttpHeaders.ContentType to listOf("application/json"),
            "X-Total-Count" to listOf("1"),
          ),
      )
    }

    val response = KtorBeersService(client).getListOfBeers(1)

    assertEquals(200, response.statusCode)
    assertEquals("1", response.header("x-total-count"))
    assertEquals("1", response.body?.single()?.id)
    client.close()
  }

  @Test
  fun `non successful response does not deserialize a body`() = runTest {
    val client = mockClient {
      respond(
        content = "not-json",
        status = HttpStatusCode.ServiceUnavailable,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }

    val response = KtorBeersService(client).getListOfBeers(1)

    assertEquals(503, response.statusCode)
    assertEquals(null, response.body)
    client.close()
  }

  @Test
  fun `malformed successful response propagates serialization failure`() = runTest {
    val client = mockClient {
      respond(
        content = "not-json",
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }

    assertFailsWith<kotlinx.serialization.SerializationException> {
      KtorBeersService(client).getListOfBeers(1)
    }
    client.close()
  }

  private fun mockClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
  ): HttpClient =
    HttpClient(MockEngine) {
      engine { addHandler(handler) }
      expectSuccess = false
      install(ContentNegotiation) {
        json(
          Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
          },
        )
      }
    }
}
