package com.standroid.shoppingapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_products")
internal data class FavoriteProductEntity(@PrimaryKey val productId: Long)
