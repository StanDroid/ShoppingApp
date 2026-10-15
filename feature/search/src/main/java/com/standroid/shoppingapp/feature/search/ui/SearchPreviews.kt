package com.standroid.shoppingapp.feature.search.ui

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.standroid.shoppingapp.feature.search.model.FavoriteProductStatus
import com.standroid.shoppingapp.feature.search.model.SearchProductUiModel
import com.standroid.shoppingapp.feature.search.model.SearchUiState
import kotlinx.coroutines.flow.MutableStateFlow
import java.math.BigDecimal

@Preview(name = "Initial", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun InitialSearchPreview() = SearchPreview(hasSearched = false)

@Preview(name = "No results", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun EmptySearchPreview() = SearchPreview()

@Preview(name = "Loading", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun LoadingSearchPreview() = SearchPreview(refresh = LoadState.Loading)

@Preview(name = "Error", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ErrorSearchPreview() = SearchPreview(refresh = LoadState.Error(IllegalStateException()))

@Preview(name = "Products", widthDp = 390, heightDp = 844, showBackground = true)
@Preview(
    name = "Large text",
    widthDp = 390,
    heightDp = 844,
    fontScale = 1.5f,
    showBackground = true
)
@Composable
private fun ProductsSearchPreview() = SearchPreview(items = previewProducts)

@Preview(
    name = "Dark",
    widthDp = 390,
    heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
private fun DarkSearchPreview() = SearchPreview(items = previewProducts, darkTheme = true)

@Preview(name = "Favorite progress", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun FavoriteProgressPreview() = SearchPreview(
    items = previewProducts.mapIndexed { index, item ->
        item.copy(
            favoriteStatus = if (index ==
                0
            ) {
                FavoriteProductStatus.InProgress
            } else {
                FavoriteProductStatus.Unavailable
            }
        )
    }
)

@Composable
private fun SearchPreview(
    hasSearched: Boolean = true,
    items: List<SearchProductUiModel> = emptyList(),
    refresh: LoadState = LoadState.NotLoading(false),
    darkTheme: Boolean = false
) {
    val flow = remember(items, refresh) {
        MutableStateFlow(
            PagingData.from(
                items,
                LoadStates(refresh, LoadState.NotLoading(true), LoadState.NotLoading(true))
            )
        )
    }
    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        SearchScreen(
            uiState = SearchUiState(
                query = if (hasSearched) "headphones" else "",
                hasSearched = hasSearched
            ),
            products = flow.collectAsLazyPagingItems(),
            onEvent = {}
        )
    }
}

private val previewProducts = listOf(
    SearchProductUiModel(
        productId = 1,
        productName = "Sony WH-1000XM5 Wireless Noise Cancelling Headphones",
        salesPriceIncVat = BigDecimal("279.99"),
        productImage = "",
        reviewAverage = 9.2,
        reviewCount = 209,
        listPriceIncVat = BigDecimal("349.00"),
        favoriteStatus = FavoriteProductStatus.Favorited
    ),
    SearchProductUiModel(
        productId = 2,
        productName = "Bose QuietComfort Ultra Headphones",
        salesPriceIncVat = BigDecimal("329.00"),
        productImage = "",
        reviewAverage = 9.5,
        reviewCount = 84,
        listPriceIncVat = null,
        favoriteStatus = FavoriteProductStatus.Unfavorited
    )
)
