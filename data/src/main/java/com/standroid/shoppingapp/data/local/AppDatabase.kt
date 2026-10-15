package com.standroid.shoppingapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.standroid.shoppingapp.data.local.dao.FavoritesDao
import com.standroid.shoppingapp.data.local.entity.FavoriteProductEntity

@Database(entities = [FavoriteProductEntity::class], version = 1, exportSchema = true)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao

    companion object {
        const val NAME = "shopping_app.db"
    }
}
