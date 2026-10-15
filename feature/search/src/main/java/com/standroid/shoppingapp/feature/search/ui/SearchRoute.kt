package com.standroid.shoppingapp.feature.search.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.standroid.shoppingapp.feature.search.R
import com.standroid.shoppingapp.feature.search.SearchViewModel
import com.standroid.shoppingapp.feature.search.model.SearchUiEffect

@Composable
fun SearchRoute(viewModel: SearchViewModel, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.searchUiState.collectAsStateWithLifecycle()
    val products = viewModel.products.collectAsLazyPagingItems()

    val snackbarHostState = remember { SnackbarHostState() }
    val contextResources = LocalResources.current

    LaunchedEffect(viewModel, lifecycleOwner, snackbarHostState, contextResources) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    SearchUiEffect.ShowFavoriteUpdateError -> snackbarHostState.showSnackbar(
                        contextResources.getString(R.string.favorite_update_error)
                    )
                }
            }
        }
    }

    SearchScreen(
        uiState = uiState,
        products = products,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}
