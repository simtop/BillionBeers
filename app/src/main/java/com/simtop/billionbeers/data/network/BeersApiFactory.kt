package com.simtop.billionbeers.data.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.lang.Exception
import java.net.HttpURLConnection

class BeersApiFactory(
    private val enableHttpLogging: Boolean
) {

    fun remoteBeersApi(): BeersService {
        val okHttpClient = OkHttpClient.Builder()

        if (enableHttpLogging) {
            val httpLoggingInterceptor = HttpLoggingInterceptor()
            httpLoggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
            okHttpClient.addInterceptor(httpLoggingInterceptor)
        }

        return Retrofit.Builder()
            .client(okHttpClient.build())
            .addConverterFactory(GsonConverterFactory.create())
            .baseUrl("https://api.punkapi.com/v2/")
            .build()
            .create(BeersService::class.java)
    }
}