package com.simtop.beer_network

import com.simtop.beer_network.network.BeersService
import com.simtop.core.network.NetworkJson
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

abstract class TestMockWebService {

  lateinit var mockServer: MockWebServer

  lateinit var apiService: BeersService

  @Before
  open fun setUp() {
    this.configureMockServer()
    generateFakeApiService()
  }

  @After
  open fun tearDown() {
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
    val response = MockResponse().setResponseCode(responseCode).setBody(getJson(fileName))
    headers.forEach { (name, value) -> response.addHeader(name, value) }
    mockServer.enqueue(response)
  }

  private fun getJson(filename: String): String {
    val resourcesDirectory = File("src/test/resources/json/${filename}")
    return String(resourcesDirectory.readBytes())
  }

  private fun generateFakeApiService() {
    apiService =
      Retrofit.Builder()
        .baseUrl(mockServer.url("/"))
        .client(generateOkHttpClient())
        .addConverterFactory(NetworkJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(BeersService::class.java)
  }

  // Deliberately no error-translating interceptor: this stack must fail exactly the way production
  // does. An earlier test-only ErrorInterceptor turned non-2xx into thrown exceptions, which hid
  // that getListOfBeers' Response<> signature was swallowing server errors into an empty page.
  private fun generateOkHttpClient() =
    OkHttpClient().newBuilder().addInterceptor(HttpLoggingInterceptor()).build()
}
