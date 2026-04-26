package com.simtop.core.di

import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.Converter
import retrofit2.converter.gson.GsonConverterFactory

@Qualifier
annotation class Named(val value: String)

@ContributesTo(AppScope::class)
interface NetworkingModule {

  companion object {
    const val BASE_URL = "baseUrl"
  }

  @Provides
  @SingleIn(AppScope::class)
  fun provideLoggingInterceptor(): HttpLoggingInterceptor {
    return HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
  }

  @Provides
  @SingleIn(AppScope::class)
  fun providesBaseHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient.Builder {
    return OkHttpClient.Builder().apply {
      // TODO: check how to do after BuildConfig Migration
      // println("Interceptor added = ${BuildConfig.DEBUG}")
      // if(BuildConfig.DEBUG) addInterceptor(loggingInterceptor)
    }
  }

  @Provides
  @SingleIn(AppScope::class)
  fun provideRetrofit(
    @Named(BASE_URL) baseUrl: String,
    converterFactory: Converter.Factory,
    httpClient: OkHttpClient.Builder
  ): Retrofit {
    return Retrofit.Builder()
      .addConverterFactory(converterFactory)
      .baseUrl(baseUrl)
      .client(httpClient.build())
      .build()
  }

  @Provides
  @SingleIn(AppScope::class)
  fun provideConverterFactory(): Converter.Factory {
    return GsonConverterFactory.create()
  }
}
