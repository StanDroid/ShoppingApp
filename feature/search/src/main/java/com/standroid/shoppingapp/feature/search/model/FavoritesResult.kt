package com.standroid.shoppingapp.feature.search.model

internal sealed interface FavoritesResult {
    data object Loading : FavoritesResult
    data class Content(val ids: Set<Long>) : FavoritesResult
    data object Failure : FavoritesResult
}
