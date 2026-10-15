package com.standroid.shoppingapp.feature.search.model

sealed interface SearchUiEffect {
    data object ShowFavoriteUpdateError : SearchUiEffect
}
