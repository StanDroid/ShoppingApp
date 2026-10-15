package com.standroid.shoppingapp.feature.search.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.standroid.shoppingapp.domain.error.ProductSearchException
import com.standroid.shoppingapp.domain.model.Product
import com.standroid.shoppingapp.domain.model.ReviewSummary
import com.standroid.shoppingapp.domain.repository.FavoritesRepository
import com.standroid.shoppingapp.domain.repository.ProductRepository
import com.standroid.shoppingapp.feature.search.SearchViewModel
import com.standroid.shoppingapp.feature.search.model.FavoriteProductStatus
import com.standroid.shoppingapp.feature.search.model.SearchProductUiModel
import com.standroid.shoppingapp.feature.search.model.SearchUiEvent
import com.standroid.shoppingapp.feature.search.model.SearchUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()

    @After fun clearViewModels() = compose.runOnIdle { store.clear() }

    @Test fun initialAndSuccessfulEmptyStatesHaveDifferentMessages() {
        val state = mutableStateOf(SearchUiState())
        compose.setContent {
            val products = remember { flowOf(page()) }.collectAsLazyPagingItems()
            MaterialTheme {
                SearchScreen(state.value, products, onEvent = {})
            }
        }
        compose.onNodeWithText("Try searching for a product").assertIsDisplayed()
        compose.onNodeWithText("No products found").assertDoesNotExist()
        compose.runOnIdle { state.value = SearchUiState(hasSearched = true) }
        compose.onNodeWithText("No products found").assertIsDisplayed()
        compose.onNodeWithText("Try searching for a product").assertDoesNotExist()
    }

    @Test fun searchIsSubmittedFromTheKeyboardAndClearOnlyChangesTheQuery() {
        val events = mutableListOf<SearchUiEvent>()
        val state = mutableStateOf(SearchUiState())
        compose.setContent {
            val products = remember { flowOf(page()) }.collectAsLazyPagingItems()
            MaterialTheme {
                SearchScreen(state.value, products, onEvent = { event ->
                    events += event
                    if (event is SearchUiEvent.QueryChanged) {
                        state.value =
                            state.value.copy(query = event.query)
                    }
                })
            }
        }
        compose.onNodeWithTag("search-input").performTextInput("Asus")
        compose.onNodeWithTag("search-input").performImeAction()
        compose.onNodeWithTag("search-input").assertIsNotFocused()
        compose.runOnIdle { assertEquals(SearchUiEvent.SearchSubmitted, events.last()) }
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.runOnIdle {
            assertEquals(SearchUiEvent.QueryChanged(""), events.last())
            assertEquals(1, events.count { it == SearchUiEvent.SearchSubmitted })
        }
    }

    @Test fun loadingTakesPriorityOverTheEmptyMessage() {
        show(flowOf(page(refresh = LoadState.Loading)))
        compose.onNodeWithText("Searching…").assertIsDisplayed()
        compose.onNodeWithText("No products found").assertDoesNotExist()
    }

    @Test fun retryLoadsProductsAfterAConnectivityFailure() {
        val attempts = AtomicInteger()
        val pages = Pager(PagingConfig(pageSize = 2, enablePlaceholders = false)) {
            object : PagingSource<Int, SearchProductUiModel>() {
                override suspend fun load(
                    params: LoadParams<Int>
                ): LoadResult<Int, SearchProductUiModel> = if (attempts.incrementAndGet() == 1) {
                    LoadResult.Error(
                        ProductSearchException.Connectivity(IOException("offline"))
                    )
                } else {
                    LoadResult.Page(listOf(product(1)), null, null)
                }

                override fun getRefreshKey(state: PagingState<Int, SearchProductUiModel>): Int? =
                    null
            }
        }.flow
        show(pages)
        waitForText("Check your internet connection and try again.")
        compose.onNodeWithText("No products found").assertDoesNotExist()
        compose.onNodeWithText("Retry").performClick()
        waitForText("Product 1")
        compose.runOnIdle { assertEquals(2, attempts.get()) }
    }

    @Test fun refreshLoadingHidesPreviouslyDisplayedProducts() {
        val items = listOf(product(1))
        val pages = MutableStateFlow(page(items))
        show(pages)
        compose.onNodeWithText("Product 1").assertIsDisplayed()

        compose.runOnIdle { pages.value = page(items, refresh = LoadState.Loading) }
        compose.onNodeWithText("Searching\u2026").assertIsDisplayed()
        compose.onNodeWithTag("product-grid").assertDoesNotExist()
        compose.onNodeWithText("Product 1").assertDoesNotExist()
    }

    @Test fun refreshErrorHidesPreviouslyDisplayedProductsUntilSuccessfulResultsArrive() {
        val items = listOf(product(1))
        val pages = MutableStateFlow(page(items))
        show(pages)
        compose.onNodeWithText("Product 1").assertIsDisplayed()

        compose.runOnIdle {
            pages.value = page(
                items,
                refresh = LoadState.Error(
                    ProductSearchException.Connectivity(IOException("offline"))
                )
            )
        }
        compose.onNodeWithText("Check your internet connection and try again.").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.onNodeWithTag("product-grid").assertDoesNotExist()
        compose.onNodeWithText("Product 1").assertDoesNotExist()

        compose.runOnIdle { pages.value = page(listOf(product(2))) }
        compose.onNodeWithText("Product 2").assertIsDisplayed()
        compose.onNodeWithText("Product 1").assertDoesNotExist()
    }

    @Test fun paginationLoadingAndFailureKeepExistingProducts() {
        val items = listOf(product(1), product(2))
        val pages = MutableStateFlow(page(items, append = LoadState.Loading))
        show(pages)
        compose.onNodeWithTag("product-grid").performScrollToIndex(2)
        compose.onNodeWithText("Loading more products…").assertIsDisplayed()
        compose.runOnIdle {
            pages.value = page(items, append = LoadState.Error(IllegalStateException("failed")))
        }
        compose.onNodeWithTag("product-grid").performScrollToIndex(2)
        compose.onNodeWithText("Couldn't load more products").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.onNodeWithTag("product-grid").performScrollToIndex(0)
        compose.onNodeWithText("Product 1").assertExists()
    }

    @Test fun cardsShowPricesAndTheFourFavoriteStates() {
        val events = mutableListOf<SearchUiEvent>()
        val pages = MutableStateFlow(
            page(listOf(product(1, FavoriteProductStatus.Favorited), product(2)))
        )
        show(pages, onEvent = { events += it })
        compose.onNodeWithContentDescription("Remove from favorites").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add to favorites").performClick()
        compose.runOnIdle { assertEquals(listOf(SearchUiEvent.FavoriteClicked(2)), events) }
        val prices = NumberFormat.getCurrencyInstance().apply {
            currency =
                Currency.getInstance("EUR")
        }
        compose.onAllNodesWithText(prices.format(BigDecimal("24.99")))[0].assertExists()
        compose.onNodeWithText(prices.format(BigDecimal("29.99"))).assertExists()
        compose.runOnIdle {
            pages.value = page(
                listOf(
                    product(1, FavoriteProductStatus.InProgress),
                    product(2, FavoriteProductStatus.Unavailable)
                )
            )
        }
        compose.onNodeWithContentDescription("Updating favorite").assertExists()
        compose.onNodeWithContentDescription("Add to favorites").assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove from favorites").assertDoesNotExist()
    }

    @Test fun favoriteFailureSnackbarRemainsVisibleWhileEditingTheQuery() {
        val products = object : ProductRepository {
            override fun search(query: String): Flow<PagingData<Product>> = flowOf(
                PagingData.from(
                    listOf(
                        Product(1, "Product 1", BigDecimal("24.99"), "", ReviewSummary(9.2, 209))
                    )
                )
            )
        }
        val favorites = object : FavoritesRepository {
            override fun observeFavoriteIds(): Flow<Set<Long>> = flowOf(emptySet())
            override suspend fun setFavorite(productId: Long, isFavorite: Boolean): Unit =
                throw IOException("database unavailable")
        }
        lateinit var viewModel: SearchViewModel
        compose.runOnIdle {
            viewModel = SearchViewModel(products, favorites)
            store.put("search", viewModel)
        }
        compose.setContent { MaterialTheme { SearchRoute(viewModel) } }
        compose.onNodeWithTag("search-input").performTextInput("Asus")
        compose.onNodeWithTag("search-input").performImeAction()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Product 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("search-input").performClick()
        compose.onNodeWithContentDescription("Add to favorites").performClick()
        waitForText("Couldn't update favorites. Please try again.")
        compose.onNodeWithText("Couldn't update favorites. Please try again.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add to favorites").assertIsDisplayed()
    }

    private fun show(
        pages: Flow<PagingData<SearchProductUiModel>>,
        onEvent: (SearchUiEvent) -> Unit = {}
    ) {
        compose.setContent {
            val products = pages.collectAsLazyPagingItems()
            MaterialTheme {
                SearchScreen(SearchUiState(hasSearched = true), products, onEvent)
            }
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text).assertExists()
    }

    private fun page(
        items: List<SearchProductUiModel> = emptyList(),
        refresh: LoadState = LoadState.NotLoading(false),
        append: LoadState = LoadState.NotLoading(true)
    ): PagingData<SearchProductUiModel> = PagingData.from(
        items,
        LoadStates(refresh, LoadState.NotLoading(true), append)
    )

    private fun product(
        id: Long,
        status: FavoriteProductStatus = FavoriteProductStatus.Unfavorited
    ): SearchProductUiModel = SearchProductUiModel(
        productId = id,
        productName = "Product $id",
        salesPriceIncVat = BigDecimal("24.99"),
        productImage = "",
        reviewAverage = 9.2,
        reviewCount = 209,
        listPriceIncVat = if (id == 1L) BigDecimal("29.99") else null,
        favoriteStatus = status
    )
}
