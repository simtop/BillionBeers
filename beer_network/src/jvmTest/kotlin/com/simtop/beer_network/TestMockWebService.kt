package com.simtop.beer_network

import com.simtop.beer_network.network.BeersService
import com.simtop.beer_network.network.KtorBeersService
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class TestMockWebService {

  lateinit var mockServer: MockWebServer

  lateinit var apiService: BeersService

  private lateinit var httpClient: HttpClient

  @BeforeTest
  open fun setUp() {
    this.configureMockServer()
    generateFakeApiService()
  }

  @AfterTest
  open fun tearDown() {
    httpClient.close()
    this.stopMockServer()
  }

  open fun configureMockServer() {
    mockServer = MockWebServer()
    mockServer.start()
  }

  open fun stopMockServer() {
    mockServer.shutdown()
  }

  open fun mockHttpResponse(
    fileName: String,
    responseCode: Int,
    headers: Map<String, String> = emptyMap(),
  ) {
    val response =
      MockResponse()
        .setResponseCode(responseCode)
        .setHeader("Content-Type", "application/json")
        .setBody(getJson(fileName))
    headers.forEach { (name, value) -> response.addHeader(name, value) }
    mockServer.enqueue(response)
  }

  private fun getJson(filename: String): String {
    val resourcesDirectory = File("src/jvmTest/resources/json/${filename}")
    return String(resourcesDirectory.readBytes())
  }

  private fun generateFakeApiService() {
    httpClient =
      HttpClient(OkHttp) {
        expectSuccess = false
        defaultRequest { url(mockServer.url("/").toString()) }
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
    apiService = KtorBeersService(httpClient)
  }
}
