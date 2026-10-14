package com.simtop.billionbeers.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.simtop.beer_database.database.ALL_MIGRATIONS
import com.simtop.beer_database.database.BeersDatabase
import com.simtop.beer_database.database.BeersDatabaseConstructor
import com.simtop.beer_network.network.configureBeersApi
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.beerdomain.domain.repositories.SavedFilterPresetsRepository
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
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

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

  val savedFilterPresetsRepository: SavedFilterPresetsRepository
    get() = graph.savedFilterPresetsRepository

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
  val savedFilterPresetsRepository: SavedFilterPresetsRepository
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
      configureBeersApi(environment)
    }

  @Provides
  @SingleIn(AppScope::class)
  @Suppress("SpreadOperator") // Room accepts migrations as varargs; the shared registry is a list.
  fun provideDatabase(config: DesktopDataConfig): BeersDatabase =
    Room.databaseBuilder(config.databasePath) { BeersDatabaseConstructor.initialize() }
      .setDriver(BundledSQLiteDriver())
      .addMigrations(*ALL_MIGRATIONS.toTypedArray())
      .build()
}
