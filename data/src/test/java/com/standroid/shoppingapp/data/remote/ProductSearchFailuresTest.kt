package com.standroid.shoppingapp.data.remote

import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import com.standroid.shoppingapp.data.remote.data.searchJson
import com.standroid.shoppingapp.data.remote.server.FakeProductServer
import com.standroid.shoppingapp.domain.error.ProductSearchException
import com.standroid.shoppingapp.domain.model.Product
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductSearchFailuresTest {
    @Test
    fun exposesHttpFailureAndSuccessfulRetry() = runBlocking {
        FakeProductServer().use { productServer ->
            productServer.enqueueJson("unavailable", status = 503)
            productServer.enqueueJson(searchJson())
            val source = ProductPagingSource("Apple", productServer.api)
            val result = source.load(
                LoadParams.Refresh(
                    null,
                    24,
                    false
                )
            ) as LoadResult.Error<Int, Product>
            assertTrue(result.throwable is ProductSearchException.Unknown)
            assertTrue(
                source.load(
                    LoadParams.Refresh(null, 24, false)
                ) is LoadResult.Page
            )
            assertEquals(2, productServer.server.requestCount)
        }
    }

    @Test
    fun rejectsMalformedTruncatedMissingNullAndMismatchedResponses() = runBlocking {
        FakeProductServer().use { productServer ->
            val invalid = listOf(
                "not JSON",
                "{\"products\":[",
                "{}",
                "null",
                "",
                searchJson(page = 2),
                searchJson().replace("\"productId\": 793652,", "")
            )
            for (json in invalid) {
                productServer.enqueueJson(json)
                val result = ProductPagingSource(
                    "Apple",
                    productServer.api
                ).load(
                    LoadParams.Refresh(
                        null,
                        24,
                        false
                    )
                ) as LoadResult.Error<Int, Product>
                assertTrue(
                    "Expected Unknown for: $json",
                    result.throwable is ProductSearchException.Unknown
                )
            }
        }
    }

    @Test
    fun rejectsNoContentResponses() = runBlocking {
        FakeProductServer().use { productServer ->
            productServer.enqueueJson("", status = 204)
            val result = ProductPagingSource("Apple", productServer.api)
                .load(
                    LoadParams.Refresh(
                        null,
                        24,
                        false
                    )
                ) as LoadResult.Error<Int, Product>
            assertTrue(result.throwable is ProductSearchException.Unknown)
        }
    }
}
