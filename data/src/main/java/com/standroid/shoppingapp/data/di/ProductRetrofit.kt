package com.standroid.shoppingapp.data.di

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

internal const val PRODUCT_BASE_URL =
    "https://bdk0sta2n0.execute-api.eu-west-1.amazonaws.com/mobile-assignment/"

internal fun createProductOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .retryOnConnectionFailure(false)
    .build()

internal fun createProductRetrofit(
    client: OkHttpClient,
    baseUrl: String = PRODUCT_BASE_URL
): Retrofit = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(client)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
