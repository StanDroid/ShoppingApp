package com.standroid.shoppingapp.feature.search.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.standroid.shoppingapp.domain.error.ProductSearchException
import com.standroid.shoppingapp.feature.search.R
import com.standroid.shoppingapp.feature.search.model.SearchProductUiModel
import com.standroid.shoppingapp.feature.search.model.SearchUiEvent
import com.standroid.shoppingapp.feature.search.model.SearchUiState

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    products: LazyPagingItems<SearchProductUiModel>,
    onEvent: (SearchUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val gridState = rememberLazyGridState()
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SearchBar(uiState, onEvent, gridState)
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
        ) {
            val emptyModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
            val refresh = products.loadState.refresh
            when {
                !uiState.hasSearched -> SearchNoResultMessage(
                    icon = R.drawable.ic_search,
                    title = stringResource(R.string.initial_search_title),
                    message = stringResource(R.string.initial_search_message),
                    modifier = emptyModifier
                )

                refresh is LoadState.Loading -> LoadingMessage(
                    text = stringResource(R.string.search_loading),
                    modifier = emptyModifier
                )

                refresh is LoadState.Error -> SearchNoResultMessage(
                    icon = R.drawable.ic_error,
                    title = stringResource(R.string.search_error_title),
                    message = errorMessage(refresh.error),
                    onRetry = { products.retry() },
                    modifier = emptyModifier
                )

                products.itemCount == 0 -> SearchNoResultMessage(
                    icon = R.drawable.ic_search,
                    title = stringResource(R.string.empty_search_title),
                    message = stringResource(R.string.empty_search_message),
                    modifier = emptyModifier
                )

                else -> ProductGrid(
                    products = products,
                    gridState = gridState,
                    onEvent = onEvent,
                    contentPadding = PaddingValues(
                        start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                        end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp
                    ),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 600.dp)
                        .fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ProductGrid(
    products: LazyPagingItems<SearchProductUiModel>,
    gridState: LazyGridState,
    onEvent: (SearchUiEvent) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier.testTag("product-grid"),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            count = products.itemCount,
            key = products.itemKey { it.productId },
            contentType = { "product" }
        ) { index ->
            products[index]?.let { product ->
                ProductCard(
                    product,
                    onFavoriteClick = {
                        onEvent(SearchUiEvent.FavoriteClicked(product.productId))
                    }
                )
            }
        }

        when (val append = products.loadState.append) {
            is LoadState.Loading ->
                item(
                    key = "append-loading",
                    span = {
                        GridItemSpan(maxLineSpan)
                    }
                ) {
                    LoadingMessage(
                        stringResource(R.string.search_loading_more),
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    )
                }

            is LoadState.Error ->
                item(
                    key = "append-error",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    PagingError(
                        title = stringResource(R.string.search_append_error),
                        failure = append.error,
                        onRetry = { products.retry() }
                    )
                }

            is LoadState.NotLoading -> Unit
        }
    }
}

@Composable
private fun SearchNoResultMessage(
    @DrawableRes icon: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .padding(24.dp)
                    .size(40.dp)
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.search_retry))
            }
        }
    }
}

@Composable
private fun LoadingMessage(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PagingError(title: String, failure: Throwable, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
        )
        Text(
            errorMessage(failure),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.search_retry))
        }
    }
}

@Composable
private fun errorMessage(failure: Throwable): String = stringResource(
    if (failure is ProductSearchException.Connectivity) {
        R.string.search_connection_error
    } else {
        R.string.search_unknown_error
    }
)
