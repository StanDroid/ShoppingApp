package com.standroid.shoppingapp.feature.search.model

sealed interface FavoriteProductStatus {
    data object Unavailable : FavoriteProductStatus
    data object InProgress : FavoriteProductStatus
    data object Favorited : FavoriteProductStatus
    data object Unfavorited : FavoriteProductStatus
}
