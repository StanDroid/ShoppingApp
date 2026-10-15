package com.standroid.shoppingapp.data.remote

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.gson.stream.MalformedJsonException
import com.standroid.shoppingapp.data.remote.api.ProductApi
import com.standroid.shoppingapp.domain.error.ProductSearchException
import com.standroid.shoppingapp.domain.model.Product
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.EOFException
import java.io.IOException
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal class ProductPagingSource(private val query: String, private val api: ProductApi) :
    PagingSource<Int, Product>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Product> {
        val page = params.key ?: 1
        return try {
            // Simulate network latency so loading states are visible.
            delay(300L.toDuration(DurationUnit.MILLISECONDS))
            val response = api.search(query, page).toSearchPage(page)
            LoadResult.Page(
                data = response.products,
                prevKey = getPrevKey(page),
                nextKey = getNextKey(response, page)
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            currentCoroutineContext().ensureActive()
            val failure = when (exception) {
                is MalformedJsonException, is EOFException ->
                    ProductSearchException.Unknown(exception)

                is IOException -> ProductSearchException.Connectivity(exception)

                else -> ProductSearchException.Unknown(exception)
            }
            LoadResult.Error(failure)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Product>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
    }

    private fun getPrevKey(page: Int): Int? = if (page > 1) page - 1 else null

    private fun getNextKey(response: SearchPage, page: Int): Int? =
        if (response.products.isNotEmpty() && page < response.pageCount) page + 1 else null
}
