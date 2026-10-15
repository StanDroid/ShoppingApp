package com.standroid.shoppingapp.data.remote.server

import com.standroid.shoppingapp.data.di.createProductOkHttpClient
import com.standroid.shoppingapp.data.di.createProductRetrofit
import com.standroid.shoppingapp.data.remote.api.ProductApi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.Closeable

internal class FakeProductServer : Closeable {
    val server = MockWebServer().apply { start() }
    private val client = createProductOkHttpClient()
    val api: ProductApi = createProductRetrofit(
        client,
        server.url("/mobile-assignment/").toString()
    )
        .create(ProductApi::class.java)

    fun enqueueJson(json: String, status: Int = 200) {
        server.enqueue(
            MockResponse().setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        )
    }

    override fun close() {
        client.dispatcher.cancelAll()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        server.shutdown()
    }
}
