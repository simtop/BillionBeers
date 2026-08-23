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
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

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
     *
     * Deferring the whole client with Retrofit's `callFactory` was considered and does not work:
     * Retrofit calls `newCall()` synchronously on the calling thread, which for a
     * `viewModelScope.launch` is the main thread (ADR 0012). That relocates the violation instead
     * of removing it.
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
  fun provideRetrofit(
    environmentConfig: EnvironmentConfig,
    converterFactory: Converter.Factory,
    okHttpClient: OkHttpClient,
  ): Retrofit {
    return Retrofit.Builder()
      .addConverterFactory(converterFactory)
      .baseUrl(environmentConfig.apiBaseUrl)
      .client(okHttpClient)
      .build()
  }

  @Provides
  @SingleIn(AppScope::class)
  fun provideConverterFactory(json: Json): Converter.Factory {
    return json.asConverterFactory("application/json".toMediaType())
  }
}
