package com.simtop.core.di

import android.content.Context
import com.simtop.core.BuildConfig
import com.simtop.core.core.EnvironmentConfig
import com.simtop.core.core.NetworkFaultController
import com.simtop.core.network.NetworkFaultInterceptor
import com.simtop.core.network.NetworkJson
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@ContributesTo(AppScope::class)
interface NetworkingModule {

  companion object {
    private const val HTTP_CACHE_SIZE_BYTES = 5L * 1024 * 1024

    /**
     * The OkHttp cache directory, resolved without touching the filesystem.
     *
     * `context.cacheDir` looks free and is not: it routes through `ContextImpl.getDataDir()`, which
     * calls `File.exists()` and creates the directory if missing. StrictMode caught it on the main
     * thread at ~500-790 ms per launch on an emulator (ADR 0012 added the detector; this was its
     * first finding), because the first composition resolves a ViewModel, which pulls the
     * repository, which builds this client - all before the first frame.
     *
     * `applicationInfo.dataDir` is a plain String field the package manager already populated, so
     * it costs no syscall, and `<dataDir>/cache` is exactly what `getCacheDir()` returns. OkHttp
     * does not touch the directory when the `Cache` is constructed either -
     * `DiskLruCache.initialize()` creates it lazily on the first cache read or write, which happens
     * on OkHttp's own dispatcher thread. So the disk work still happens, just not on the main
     * thread.
     */
    private fun httpCacheDir(context: Context): File =
      File(context.applicationInfo.dataDir, "cache/http")
  }

  @Provides @SingleIn(AppScope::class) fun provideJson(): Json = NetworkJson

  @Provides
  @SingleIn(AppScope::class)
  fun provideLoggingInterceptor(): HttpLoggingInterceptor {
    return HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
  }

  @Provides
  @SingleIn(AppScope::class)
  fun provideOkHttpClient(
    environmentConfig: EnvironmentConfig,
    loggingInterceptor: HttpLoggingInterceptor,
    networkFaultController: NetworkFaultController,
    @ApplicationContext context: Context,
  ): OkHttpClient {
    return OkHttpClient.Builder()
      .connectTimeout(environmentConfig.connectTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
      .readTimeout(environmentConfig.readTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
      .writeTimeout(environmentConfig.writeTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
      .cache(Cache(httpCacheDir(context), HTTP_CACHE_SIZE_BYTES))
      .apply {
        if (BuildConfig.DEBUG) {
          addInterceptor(loggingInterceptor)
          addInterceptor(NetworkFaultInterceptor(networkFaultController))
        }
      }
      .build()
  }

  @Provides
  @SingleIn(AppScope::class)
  fun provideHttpClient(
    environmentConfig: EnvironmentConfig,
    json: Json,
    okHttpClient: OkHttpClient,
  ): HttpClient =
    HttpClient(OkHttp) {
      engine { preconfigured = okHttpClient }
      expectSuccess = false
      defaultRequest { url(environmentConfig.apiBaseUrl) }
      install(ContentNegotiation) { json(json) }
    }
}
