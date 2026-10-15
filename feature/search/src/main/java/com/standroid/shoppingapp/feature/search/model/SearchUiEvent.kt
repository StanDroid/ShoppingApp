package com.standroid.shoppingapp.feature.search.model

sealed interface SearchUiEvent {
    data class QueryChanged(val query: String) : SearchUiEvent
    data object SearchSubmitted : SearchUiEvent
    data class FavoriteClicked(val productId: Long) : SearchUiEvent
}
