package com.standroid.shoppingapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.standroid.shoppingapp.data.local.entity.FavoriteProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface FavoritesDao {
    @Query("SELECT productId FROM favorite_products ORDER BY productId")
    fun observeIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteProductEntity)

    @Query("DELETE FROM favorite_products WHERE productId = :productId")
    suspend fun delete(productId: Long)
}
