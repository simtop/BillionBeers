package com.simtop.billionbeers.iosshared

import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.DefaultCoroutineDispatcherProvider
import com.simtop.core.core.EnvironmentConfig
import com.simtop.core.core.LanguageProvider
import com.simtop.core.core.Logger
import com.simtop.core.core.NoOpLogger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers

/** Host configuration for the future iOS UI; this module intentionally contains no UI. */
public data class IosDataConfig(
  public val apiBaseUrl: String,
  public val languageCode: String = "en",
)

/** Narrow lifecycle boundary for the future iOS host. */
public class IosDataRuntime private constructor(private val graph: IosGraph) {

  // Resolve graph-owned resources once so shutdown closes the exact instances used by consumers.
  private val httpClient = graph.httpClient
  private val database = graph.database
  private var closed = false

  public val repository: BeersRepository
    get() = graph.repository

  public val pagerFactory: BeersPagerFactory
    get() = graph.pagerFactory

  internal val coroutineDispatcherProvider: CoroutineDispatcherProvider
    get() = graph.coroutineDispatcherProvider

  internal suspend fun loadImage(url: String): ByteArray? =
    runCatching { httpClient.get(url).body<ByteArray>() }.getOrNull()

  public fun close() {
    if (!closed) {
      closed = true
      httpClient.close()
      database.close()
    }
  }

  public companion object {
    public fun open(config: IosDataConfig): IosDataRuntime =
      IosDataRuntime(createGraphFactory<IosGraph.Factory>().create(config))
  }
}

@DependencyGraph(AppScope::class)
internal interface IosGraph {
  val repository: BeersRepository
  val pagerFactory: BeersPagerFactory
  val coroutineDispatcherProvider: CoroutineDispatcherProvider
  val httpClient: HttpClient
  val database: BeersDatabase

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Provides config: IosDataConfig): IosGraph
  }
}

@ContributesTo(AppScope::class)
internal interface IosHostModule {

  @Provides
  fun provideEnvironmentConfig(config: IosDataConfig): EnvironmentConfig =
    EnvironmentConfig(apiBaseUrl = config.apiBaseUrl)

  @Provides
  fun provideLanguageProvider(config: IosDataConfig): LanguageProvider =
    LanguageProvider { config.languageCode }

  @Provides fun provideLogger(): Logger = NoOpLogger()

  @Provides
  fun provideDispatcherProvider(): CoroutineDispatcherProvider =
    object : CoroutineDispatcherProvider {
      override val io = Dispatchers.Default
    }

  @Provides
  @SingleIn(AppScope::class)
  fun provideHttpClient(environment: EnvironmentConfig): HttpClient =
    createBridgeHttpClient(environment)

  @Provides
  @SingleIn(AppScope::class)
  fun provideDatabase(): BeersDatabase = createBridgeDatabase()
}

internal expect fun createBridgeHttpClient(environment: EnvironmentConfig): HttpClient

internal expect fun createBridgeDatabase(): BeersDatabase
