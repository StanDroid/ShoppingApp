package com.standroid.shoppingapp.data.remote

import androidx.paging.PagingConfig
import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import com.standroid.shoppingapp.data.remote.data.productJson
import com.standroid.shoppingapp.data.remote.data.searchJson
import com.standroid.shoppingapp.data.remote.server.FakeProductServer
import com.standroid.shoppingapp.domain.model.Product
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class ProductPagingSourceTest {
    @Test
    fun encodesQueryAndUsesOneBasedPagesRegardlessOfLoadSize() = runBlocking {
        FakeProductServer().use { fakeProductServer ->
            fakeProductServer.enqueueJson(searchJson())
            val query = "Asus & USB+C/\u00E9"
            val result = ProductPagingSource(
                query,
                fakeProductServer.api
            ).load(
                LoadParams.Refresh(
                    null,
                    72,
                    false
                )
            ) as LoadResult.Page<Int, Product>

            val request = requireNotNull(
                fakeProductServer.server.takeRequest(
                    5,
                    TimeUnit.SECONDS
                )
            )

            assertEquals("GET", request.method)
            assertEquals("/mobile-assignment/search", request.requestUrl?.encodedPath)
            assertEquals(query, request.requestUrl?.queryParameter("que"))
            assertEquals("1", request.requestUrl?.queryParameter("page"))
            assertNull(request.requestUrl?.queryParameter("pageSize"))
            assertNull(result.prevKey)
            assertEquals(2, result.nextKey)
            assertEquals(793652L, result.data.single().productId)
            assertEquals(1, fakeProductServer.server.requestCount)
        }
    }

    @Test
    fun usesMetadataForMiddleAndFinalPagesAndStopsOnEmptyPages() = runBlocking {
        FakeProductServer().use { productServer ->
            for (page in listOf(2, 3, 4)) {
                productServer.enqueueJson(
                    searchJson(
                        page = page,
                        products = if (page == 4) "" else productJson()
                    )
                )
                val result = ProductPagingSource("Apple", productServer.api)
                    .load(
                        LoadParams.Append(
                            page,
                            24,
                            false
                        )
                    ) as LoadResult.Page<Int, Product>
                assertEquals(page - 1, result.prevKey)
                assertEquals(if (page == 2) 3 else null, result.nextKey)
            }
        }
    }

    @Test
    fun computesRefreshKeyFromAnchorAndHandlesNoAnchor() = runBlocking {
        FakeProductServer().use { productServer ->
            productServer.enqueueJson(searchJson(page = 2))
            val source = ProductPagingSource("Apple", productServer.api)
            val page = source.load(
                LoadParams.Append(
                    2,
                    24,
                    false
                )
            ) as LoadResult.Page<Int, Product>
            assertEquals(
                2,
                source.getRefreshKey(
                    PagingState(
                        listOf(page),
                        0,
                        PagingConfig(24),
                        0
                    )
                )
            )
            assertNull(
                source.getRefreshKey(
                    PagingState(
                        listOf(page),
                        null,
                        PagingConfig(24),
                        0
                    )
                )
            )
        }
    }
}
