package com.standroid.shoppingapp.domain.repository

import androidx.paging.PagingData
import com.standroid.shoppingapp.domain.model.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    fun search(query: String): Flow<PagingData<Product>>
}
