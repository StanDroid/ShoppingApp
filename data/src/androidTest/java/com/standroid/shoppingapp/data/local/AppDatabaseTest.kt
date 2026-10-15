package com.standroid.shoppingapp.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.standroid.shoppingapp.data.repository.FavoritesRepositoryImpl
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun observesIdempotentInsertsAndDeletes() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repository = FavoritesRepositoryImpl(database.favoritesDao())
            assertEquals(
                emptySet<Long>(),
                withTimeout(5_000.toDuration(DurationUnit.MILLISECONDS)) {
                    repository.observeFavoriteIds().first()
                }
            )
            val inserted = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000.toDuration(DurationUnit.MILLISECONDS)) {
                    repository.observeFavoriteIds().first { it.contains(Long.MAX_VALUE) }
                }
            }
            repository.setFavorite(Long.MAX_VALUE, true)
            assertEquals(setOf(Long.MAX_VALUE), inserted.await())
            repository.setFavorite(Long.MAX_VALUE, true)
            repository.setFavorite(793652, true)
            assertEquals(
                setOf(Long.MAX_VALUE, 793652L),
                withTimeout(
                    5_000.toDuration(DurationUnit.MILLISECONDS)
                ) { repository.observeFavoriteIds().first() }
            )
            repository.setFavorite(Long.MAX_VALUE, false)
            repository.setFavorite(Long.MAX_VALUE, false)
            assertEquals(
                setOf(793652L),
                withTimeout(5_000.toDuration(DurationUnit.MILLISECONDS)) {
                    repository.observeFavoriteIds().first()
                }
            )
            val deleted = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(
                    5_000.toDuration(DurationUnit.MILLISECONDS)
                ) { repository.observeFavoriteIds().first { it.isEmpty() } }
            }
            repository.setFavorite(793652, false)
            assertEquals(emptySet<Long>(), deleted.await())
        } finally {
            database.close()
        }
    }

    @Test
    fun persistsFavoriteIdsAfterReopening() = runBlocking {
        val name = "favorites-persistence-test.db"
        context.deleteDatabase(name)
        try {
            val first = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            try {
                FavoritesRepositoryImpl(first.favoritesDao()).setFavorite(793652, true)
            } finally {
                first.close()
            }
            val reopened = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            try {
                val repository = FavoritesRepositoryImpl(reopened.favoritesDao())
                assertEquals(
                    setOf(793652L),
                    withTimeout(5_000.toDuration(DurationUnit.MILLISECONDS)) {
                        repository.observeFavoriteIds().first()
                    }
                )
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
