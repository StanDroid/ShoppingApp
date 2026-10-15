package com.standroid.shoppingapp.domain.repository

import kotlinx.coroutines.flow.Flow

interface FavoritesRepository {
    fun observeFavoriteIds(): Flow<Set<Long>>
    suspend fun setFavorite(productId: Long, isFavorite: Boolean)
}
