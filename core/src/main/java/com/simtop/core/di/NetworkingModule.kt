package com.simtop.core.di

import android.content.Context
import com.simtop.core.BuildConfig
import com.simtop.core.core.NetworkFaultController
import com.simtop.core.network.NetworkFaultInterceptor
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
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
    const val BASE_URL = "baseUrl"
    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val HTTP_CACHE_SIZE_BYTES = 5L * 1024 * 1024
  }

  @Provides
  @SingleIn(AppScope::class)
  fun provideJson(): Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
  }

  @Provides
  @SingleIn(AppScope::class)
  fun provideLoggingInterceptor(): HttpLoggingInterceptor {
    return HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
  }

  @Provides
  @SingleIn(AppScope::class)
  fun provideOkHttpClient(
    loggingInterceptor: HttpLoggingInterceptor,
    networkFaultController: NetworkFaultController,
    @ApplicationContext context: Context,
  ): OkHttpClient {
    return OkHttpClient.Builder()
      .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .cache(Cache(File(context.cacheDir, "http"), HTTP_CACHE_SIZE_BYTES))
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
    @Named(BASE_URL) baseUrl: String,
    converterFactory: Converter.Factory,
    okHttpClient: OkHttpClient,
  ): Retrofit {
    return Retrofit.Builder()
      .addConverterFactory(converterFactory)
      .baseUrl(baseUrl)
      .client(okHttpClient)
      .build()
  }

  @Provides
  @SingleIn(AppScope::class)
  fun provideConverterFactory(json: Json): Converter.Factory {
    return json.asConverterFactory("application/json".toMediaType())
  }
}
