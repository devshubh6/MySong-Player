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

    val gson: Gson = GsonBuilder()
        .setLenient()
        .serializeNulls()
        .create()

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    val api: JioSaavnApi by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.API_BASE)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(JioSaavnApi::class.java)
    }
}
