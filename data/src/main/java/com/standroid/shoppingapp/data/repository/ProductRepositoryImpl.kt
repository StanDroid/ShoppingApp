package com.standroid.shoppingapp.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.standroid.shoppingapp.data.remote.ProductPagingSource
import com.standroid.shoppingapp.data.remote.api.ProductApi
import com.standroid.shoppingapp.domain.model.Product
import com.standroid.shoppingapp.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

internal class ProductRepositoryImpl @Inject constructor(private val api: ProductApi) :
    ProductRepository {
    override fun search(query: String): Flow<PagingData<Product>> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return flowOf(PagingData.empty())
        return Pager(
            config = PagingConfig(
                pageSize = 24,
                initialLoadSize = 24,
                prefetchDistance = 6,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                ProductPagingSource(normalizedQuery, api)
            }
        ).flow
    }
}
