package com.standroid.shoppingapp.data.repository

import com.standroid.shoppingapp.data.local.dao.FavoritesDao
import com.standroid.shoppingapp.data.local.entity.FavoriteProductEntity
import com.standroid.shoppingapp.domain.repository.FavoritesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal class FavoritesRepositoryImpl @Inject constructor(private val dao: FavoritesDao) :
    FavoritesRepository {
    override fun observeFavoriteIds(): Flow<Set<Long>> = dao.observeIds()
        .onStart {
            // Simulate initial database latency without delaying subsequent updates.
            delay(300L.toDuration(DurationUnit.MILLISECONDS))
        }
        .map { it.toSet() }
        .distinctUntilChanged()

    override suspend fun setFavorite(productId: Long, isFavorite: Boolean) {
        // Simulate database write latency so favorite progress is visible.
        delay(300L.toDuration(DurationUnit.MILLISECONDS))
        if (isFavorite) dao.insert(FavoriteProductEntity(productId)) else dao.delete(productId)
    }
}
