package com.standroid.shoppingapp.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.standroid.shoppingapp.domain.model.Product
import com.standroid.shoppingapp.domain.repository.FavoritesRepository
import com.standroid.shoppingapp.domain.repository.ProductRepository
import com.standroid.shoppingapp.feature.search.model.FavoriteProductStatus
import com.standroid.shoppingapp.feature.search.model.FavoritesResult
import com.standroid.shoppingapp.feature.search.model.SearchProductUiModel
import com.standroid.shoppingapp.feature.search.model.SearchUiEffect
import com.standroid.shoppingapp.feature.search.model.SearchUiEvent
import com.standroid.shoppingapp.feature.search.model.SearchUiState
import com.standroid.shoppingapp.feature.search.model.toSearchUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    private val queryInput = MutableStateFlow("")
    private val submittedSearch = MutableStateFlow<SearchRequest?>(null)
    private val favoriteUpdate = MutableStateFlow<FavoriteUpdate?>(null)

    val searchUiState: StateFlow<SearchUiState> =
        combine(
            queryInput,
            submittedSearch
        ) { query, request ->
            SearchUiState(query = query, hasSearched = request != null)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SearchUiState()
        )

    private val _effects = MutableSharedFlow<SearchUiEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val effects: SharedFlow<SearchUiEffect> = _effects.asSharedFlow()

    private val favorites = flow {
        emitAll(favoritesRepository.observeFavoriteIds())
    }
        .map<Set<Long>, FavoritesResult> { FavoritesResult.Content(it) }
        .catch { failure ->
            if (failure !is Exception || failure is CancellationException) throw failure
            currentCoroutineContext().ensureActive()
            emit(FavoritesResult.Failure)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FavoritesResult.Loading
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val cachedProducts = submittedSearch
        .flatMapLatest { request ->
            if (request == null) {
                flowOf(PagingData.empty<RequestedProduct>())
            } else {
                productRepository.search(request.query)
                    .map { data -> data.map { RequestedProduct(request.id, it) } }
                    .onStart {
                        emit(
                            PagingData.empty(
                                sourceLoadStates = LoadStates(
                                    refresh = LoadState.Loading,
                                    prepend = LoadState.NotLoading(false),
                                    append = LoadState.NotLoading(false)
                                )
                            )
                        )
                    }
            }
        }.cachedIn(viewModelScope)

    val products: Flow<PagingData<SearchProductUiModel>> =
        combine(
            cachedProducts,
            submittedSearch,
            favorites,
            favoriteUpdate
        ) { data, request, favoritesResult, update ->
            data.filter { item ->
                item.requestId == request?.id && item.requestId == submittedSearch.value?.id
            }.map { item ->
                item.product.toSearchUiModel(
                    mapFavoriteStatus(
                        item.product.productId,
                        favoritesResult,
                        update
                    )
                )
            }
        }

    fun onEvent(event: SearchUiEvent) {
        when (event) {
            is SearchUiEvent.QueryChanged -> onQueryChanged(event.query)
            SearchUiEvent.SearchSubmitted -> submitSearch()
            is SearchUiEvent.FavoriteClicked -> onFavoriteClick(event.productId)
        }
    }

    private fun onQueryChanged(query: String) {
        queryInput.value = query
    }

    private fun submitSearch() {
        val query = queryInput.value.trim()
        if (query.isEmpty()) return
        submittedSearch.update { previous ->
            if (query == previous?.query) {
                previous
            } else {
                SearchRequest(
                    id = (previous?.id ?: 0L) + 1,
                    query = query
                )
            }
        }
    }

    private fun onFavoriteClick(productId: Long) {
        if (favoriteUpdate.value != null) return
        val content = favorites.value as? FavoritesResult.Content ?: return
        val update = FavoriteUpdate(productId, desiredState = productId !in content.ids)
        favoriteUpdate.value = update

        viewModelScope.launch {
            try {
                toggleFavorite(update)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                _effects.tryEmit(SearchUiEffect.ShowFavoriteUpdateError)
            } finally {
                favoriteUpdate.value = null
            }
        }
    }

    private suspend fun toggleFavorite(update: FavoriteUpdate) {
        favoritesRepository.setFavorite(update.productId, update.desiredState)
        favorites.first { result ->
            result is FavoritesResult.Failure ||
                result is FavoritesResult.Content &&
                (update.productId in result.ids) == update.desiredState
        }
    }

    private fun mapFavoriteStatus(
        productId: Long,
        result: FavoritesResult,
        update: FavoriteUpdate?
    ): FavoriteProductStatus = when {
        result !is FavoritesResult.Content -> FavoriteProductStatus.Unavailable
        update?.productId == productId -> FavoriteProductStatus.InProgress
        productId in result.ids -> FavoriteProductStatus.Favorited
        else -> FavoriteProductStatus.Unfavorited
    }

    private data class SearchRequest(val id: Long, val query: String)

    private data class FavoriteUpdate(val productId: Long, val desiredState: Boolean)

    private data class RequestedProduct(val requestId: Long, val product: Product)
}
