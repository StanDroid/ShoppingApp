package com.standroid.shoppingapp.data.repository

import com.standroid.shoppingapp.data.local.dao.FavoritesDao
import com.standroid.shoppingapp.data.local.entity.FavoriteProductEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class FavoritesRepositoryImplTest {
    @Test fun mapsIdsToSetsAndPropagatesWriteFailures() = runTest {
        val failure = IllegalStateException("disk full")
        val dao = object : FavoritesDao {
            override fun observeIds(): Flow<List<Long>> = MutableStateFlow(listOf(1L, 1L, 2L))
            override suspend fun insert(favorite: FavoriteProductEntity): Unit = throw failure
            override suspend fun delete(productId: Long): Unit = throw failure
        }
        val repository = FavoritesRepositoryImpl(dao)
        assertEquals(setOf(1L, 2L), repository.observeFavoriteIds().first())
        for (state in listOf(true, false)) {
            try {
                repository.setFavorite(1, state)
                fail("Database write failure must propagate")
            } catch (actual: IllegalStateException) {
                assertSame(failure, actual)
            }
        }
    }
}
