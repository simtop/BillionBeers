package com.simtop.beer_network.network

import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.models.BreweryApiResponseItem
import com.simtop.beer_network.models.TypologyApiResponseItem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

fun createKtorBeersService(client: HttpClient): BeersService = KtorBeersService(client)

internal class KtorBeersService(private val client: HttpClient) : BeersService {
  override suspend fun getListOfBeers(
    page: Int,
    perPage: Int,
    languageCode: String,
    search: String?,
    typologyId: String?,
    breweryId: String?,
  ): BeersServiceResponse<List<BeersApiResponseItem>> =
    request("beers") {
      parameter("_page", page)
      parameter("_limit", perPage)
      parameter("translations.language.code", languageCode)
      search?.let { encodedParameter("q", it) }
      typologyId?.let { encodedParameter("typology.id", it) }
      breweryId?.let { encodedParameter("brewery.id", it) }
    }

  override suspend fun getTypologies(): BeersServiceResponse<List<TypologyApiResponseItem>> =
    request("typologies")

  override suspend fun getBreweries(): BeersServiceResponse<List<BreweryApiResponseItem>> =
    request("breweries")

  private suspend inline fun <reified T> request(
    path: String,
    crossinline configure: HttpRequestBuilder.() -> Unit = {},
  ): BeersServiceResponse<T> {
    val response =
      try {
        client.get(path, configure)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        throw BeersServiceNetworkException(exception)
      }
    return BeersServiceResponse(
      body = response.bodyIfSuccessful(),
      statusCode = response.status.value,
      headers =
        response.headers.entries().associate { (name, values) ->
          name to values.joinToString(",")
        },
    )
  }

  private fun HttpRequestBuilder.encodedParameter(name: String, value: String) {
    url { encodedParameters.append(name, value.percentEncode()) }
  }

  private fun String.percentEncode(): String =
    encodeToByteArray().joinToString(separator = "") { byte ->
      val value = byte.toInt() and 0xff
      if (value.isQueryComponentSafe()) {
        value.toChar().toString()
      } else {
        "%${value.toString(16).uppercase().padStart(2, '0')}"
      }
    }

  private fun Int.isQueryComponentSafe(): Boolean =
    this in 'a'.code..'z'.code ||
      this in 'A'.code..'Z'.code ||
      this in '0'.code..'9'.code ||
      this == '-'.code ||
      this == '.'.code ||
      this == '_'.code ||
      this == '~'.code

  private suspend inline fun <reified T> HttpResponse.bodyIfSuccessful(): T? {
    if (status.value !in HTTP_SUCCESS_RANGE) return null

    return try {
      body<T?>()
    } catch (exception: JsonConvertException) {
      throw exception.cause as? SerializationException ?: exception
    }
  }

  private companion object {
    val HTTP_SUCCESS_RANGE = 200..299
  }
}
