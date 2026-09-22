package com.simtop.billionbeers.web

import com.simtop.beer_storage.api.BeersStorage
import com.simtop.beer_storage.browser.IndexedDbBeersStorage
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.EnvironmentConfig
import com.simtop.core.core.EpochTimeProvider
import com.simtop.core.core.LanguageProvider
import com.simtop.core.core.Logger
import com.simtop.core.core.NoOpLogger
import com.simtop.core.core.SystemEpochTimeProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.w3c.dom.url.URLSearchParams

const val DEFAULT_WEB_API_BASE_URL = "https://brewbuddy.dev/"
const val DEFAULT_WEB_IMAGE_PROXY_BASE_URL = "http://127.0.0.1:8787/image"

data class WebDataConfig(
  val apiBaseUrl: String = DEFAULT_WEB_API_BASE_URL,
  val languageCode: String = "en",
  val imageProxyBaseUrl: String? = null,
)

/** Owns the browser data graph and every resource it creates. */
class WebDataRuntime private constructor(private val graph: WebGraph) {

  private var closed = false

  val repository: BeersRepository
    get() = graph.repository

  val pagerFactory: BeersPagerFactory
    get() = graph.pagerFactory

  val storage: BeersStorage
    get() = graph.browserStorage

  internal suspend fun loadImage(url: String): ByteArray? =
    runCatching {
        val requestUrl =
          graph.config.imageProxyBaseUrl?.let { proxyBaseUrl ->
            URLSearchParams().apply { append("url", url) }.let { "$proxyBaseUrl?${it.toString()}" }
          } ?: url
        graph.httpClient.get(requestUrl).body<ByteArray>()
      }
      .getOrNull()

  fun close() {
    if (!closed) {
      closed = true
      graph.browserStorage.close()
      graph.httpClient.close()
    }
  }

  companion object {
    suspend fun open(config: WebDataConfig = WebDataConfig()): WebDataRuntime {
      val graph = createGraphFactory<WebGraph.Factory>().create(config)
      graph.browserStorage.awaitReady()
      return WebDataRuntime(graph)
    }
  }
}

@DependencyGraph(AppScope::class)
interface WebGraph {
  val repository: BeersRepository
  val pagerFactory: BeersPagerFactory
  val httpClient: HttpClient
  val browserStorage: IndexedDbBeersStorage
  val config: WebDataConfig

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Provides config: WebDataConfig): WebGraph
  }
}

@ContributesTo(AppScope::class)
interface WebHostModule {

  @Provides
  @SingleIn(AppScope::class)
  fun provideEnvironmentConfig(config: WebDataConfig): EnvironmentConfig =
    EnvironmentConfig(apiBaseUrl = config.apiBaseUrl)

  @Provides
  fun provideLanguageProvider(config: WebDataConfig): LanguageProvider = LanguageProvider {
    config.languageCode
  }

  @Provides fun provideEpochTimeProvider(): EpochTimeProvider = SystemEpochTimeProvider()

  @Provides fun provideLogger(): Logger = NoOpLogger()

  @Provides
  @SingleIn(AppScope::class)
  fun provideHttpClient(environment: EnvironmentConfig): HttpClient =
    HttpClient(Js) {
      expectSuccess = false
      defaultRequest { url(environment.apiBaseUrl) }
      install(ContentNegotiation) {
        json(
          Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
          }
        )
      }
    }
}
