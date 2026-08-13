package com.shubh.song.data.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.shubh.song.util.Constants
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    /** Browser-like UA so the JioSaavn media CDN doesn't 403 the stream. */
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    val gson: Gson = GsonBuilder()
        .setLenient()
        .serializeNulls()
        .create()

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // JioSaavn + its CDN (aac.saavncdn.com) reject the stock OkHttp
        // "okhttp/x.y" User-Agent with a 403, which made every song silently
        // fail to load. Send a browser-like UA so streaming works.
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Referer", Constants.API_BASE)
                .build()
            chain.proceed(req)
        }
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    val api: JioSaavnApi by lazy {
        // Retrofit requires a base URL that ends with "/" or it throws
        // "Base URL must end in /". Guarantee the trailing slash ourselves
        // so a missing one in BuildConfig.API_BASE_URL can never break fetch.
        val baseUrl = if (Constants.API_BASE.endsWith("/")) {
            Constants.API_BASE
        } else {
            "${Constants.API_BASE}/"
        }
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(JioSaavnApi::class.java)
    }
}
