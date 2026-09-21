package com.simtop.billionbeers.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_database.database.BeersDatabaseConstructor
import com.simtop.beer_database.database.MIGRATION_1_2
import com.simtop.beer_database.database.MIGRATION_2_3
import com.simtop.beer_database.database.MIGRATION_3_4
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
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.Json

/** Configuration owned by a desktop host, not by the shared data layer. */
const val DEFAULT_API_BASE_URL = "https://brewbuddy.dev/"

data class DesktopDataConfig(
  val databasePath: String,
  val apiBaseUrl: String = DEFAULT_API_BASE_URL,
  val languageCode: String = "en",
)

/** Owns the closeable resources used by one desktop data graph. */
class DesktopDataRuntime private constructor(private val graph: DesktopGraph) : Closeable {

  private val closed = AtomicBoolean(false)

  val repository: BeersRepository
    get() = graph.repository

  val pagerFactory: BeersPagerFactory
    get() = graph.pagerFactory

  override fun close() {
    if (closed.compareAndSet(false, true)) {
      graph.httpClient.close()
      graph.database.close()
    }
  }

  companion object {
    fun open(config: DesktopDataConfig): DesktopDataRuntime {
      File(config.databasePath).parentFile?.mkdirs()
      return DesktopDataRuntime(createGraphFactory<DesktopGraph.Factory>().create(config))
    }
  }
}

@DependencyGraph(AppScope::class)
interface DesktopGraph {
  val repository: BeersRepository
  val pagerFactory: BeersPagerFactory
  val httpClient: HttpClient
  val database: BeersDatabase

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Provides config: DesktopDataConfig): DesktopGraph
  }
}

@ContributesTo(AppScope::class)
interface DesktopHostModule {

  @Provides
  @SingleIn(AppScope::class)
  fun provideEnvironmentConfig(config: DesktopDataConfig): EnvironmentConfig =
    EnvironmentConfig(apiBaseUrl = config.apiBaseUrl)

  @Provides
  fun provideLanguageProvider(config: DesktopDataConfig): LanguageProvider = LanguageProvider {
    config.languageCode
  }

  @Provides fun provideLogger(): Logger = NoOpLogger()

  @Provides
  fun provideDispatcherProvider(): CoroutineDispatcherProvider =
    DefaultCoroutineDispatcherProvider()

  @Provides
  @SingleIn(AppScope::class)
  fun provideHttpClient(environment: EnvironmentConfig): HttpClient =
    HttpClient(OkHttp) {
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

  @Provides
  @SingleIn(AppScope::class)
  fun provideDatabase(config: DesktopDataConfig): BeersDatabase =
    Room.databaseBuilder(config.databasePath) { BeersDatabaseConstructor.initialize() }
      .setDriver(BundledSQLiteDriver())
      .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
      .build()
}
