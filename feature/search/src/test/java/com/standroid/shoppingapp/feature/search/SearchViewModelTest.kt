package com.standroid.shoppingapp.feature.search

import androidx.lifecycle.ViewModelStore
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.testing.asSnapshot
import com.standroid.shoppingapp.domain.model.Product
import com.standroid.shoppingapp.domain.model.ReviewSummary
import com.standroid.shoppingapp.domain.repository.FavoritesRepository
import com.standroid.shoppingapp.domain.repository.ProductRepository
import com.standroid.shoppingapp.feature.search.model.FavoriteProductStatus
import com.standroid.shoppingapp.feature.search.model.SearchProductUiModel
import com.standroid.shoppingapp.feature.search.model.SearchUiEffect
import com.standroid.shoppingapp.feature.search.model.SearchUiEvent
import com.standroid.shoppingapp.feature.search.model.SearchUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()
    private val store = ViewModelStore()
    private val repository = FakeProducts()
    private val favorites = FakeFavorites()
    private var viewModelCount = 0

    @After
    fun clearViewModels() {
        store.clear()
        mainDispatcher.dispatcher.scheduler.runCurrent()
    }

    @Test
    fun startsEmptyAndSearchesOnlyOnNonBlankSubmission() = runTest {
        val vm = viewModel()
        val result = collectProducts(vm)
        runCurrent()
        assertEquals(SearchUiState(), vm.searchUiState.value)
        assertTrue(result.items.isEmpty())
        vm.onEvent(SearchUiEvent.QueryChanged(" \t "))
        vm.onEvent(SearchUiEvent.SearchSubmitted)
        runCurrent()
        assertTrue(repository.requests.isEmpty())
        assertFalse(vm.searchUiState.value.hasSearched)

        vm.onEvent(SearchUiEvent.QueryChanged("  Asus  "))
        runCurrent()
        assertTrue(repository.requests.isEmpty())
        vm.onEvent(SearchUiEvent.SearchSubmitted)
        runCurrent()
        assertEquals("Asus", repository.requests.single().query)
        assertEquals(LoadState.Loading, result.refresh)
        repository.requests.single().complete(1)
        runCurrent()
        assertEquals(listOf(1L), result.ids)
        assertEquals("  Asus  ", vm.searchUiState.value.query)
        assertTrue(vm.searchUiState.value.hasSearched)

        vm.onEvent(SearchUiEvent.QueryChanged("Asus"))
        vm.onEvent(SearchUiEvent.SearchSubmitted)
        vm.onEvent(SearchUiEvent.QueryChanged("Apple"))
        runCurrent()
        assertEquals(1, repository.requests.size)
        assertEquals(listOf(1L), result.ids)
        vm.onEvent(SearchUiEvent.QueryChanged("  "))
        vm.onEvent(SearchUiEvent.SearchSubmitted)
        runCurrent()
        assertTrue(vm.searchUiState.value.hasSearched)
        assertEquals(listOf(1L), result.ids)
    }

    @Test
    fun derivesUiStateWithoutStartingSearchUntilProductsAreCollected() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.searchUiState.collect {}
        }
        vm.onEvent(SearchUiEvent.QueryChanged("  Asus  "))
        vm.onEvent(SearchUiEvent.SearchSubmitted)
        vm.onEvent(SearchUiEvent.QueryChanged("Apple draft"))
        runCurrent()
        assertEquals(
            SearchUiState(query = "Apple draft", hasSearched = true),
            vm.searchUiState.value
        )
        assertTrue(repository.requests.isEmpty())

        collectProducts(vm)
        runCurrent()
        assertEquals("Asus", repository.requests.single().query)
        vm.onEvent(SearchUiEvent.QueryChanged(""))
        vm.onEvent(SearchUiEvent.SearchSubmitted)
        runCurrent()
        assertEquals(SearchUiState(hasSearched = true), vm.searchUiState.value)
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun distinguishesLoadingFromAnEmptySuccessAndAnError() = runTest {
        val emptyResponse = CompletableDeferred<Unit>()
        val errorResponse = CompletableDeferred<Unit>()
        val failure = IllegalStateException("search failed")
        val pagedRepository = object : ProductRepository {
            override fun search(query: String): Flow<PagingData<Product>> = Pager(
                PagingConfig(pageSize = 24, enablePlaceholders = false)
            ) {
                object : PagingSource<Int, Product>() {
                    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Product> =
                        if (query == "empty") {
                            emptyResponse.await()
                            LoadResult.Page(emptyList(), null, null)
                        } else {
                            errorResponse.await()
                            LoadResult.Error(failure)
                        }

                    override fun getRefreshKey(state: PagingState<Int, Product>): Int? = null
                }
            }.flow
        }
        val vm = viewModel(products = pagedRepository)
        val result = collectProducts(vm)
        runCurrent()
        assertFalse(vm.searchUiState.value.hasSearched)
        assertTrue(result.items.isEmpty())

        submit(vm, "empty")
        assertTrue(vm.searchUiState.value.hasSearched)
        assertEquals(LoadState.Loading, result.refresh)
        emptyResponse.complete(Unit)
        runCurrent()
        assertTrue(result.refresh is LoadState.NotLoading)
        assertTrue(result.items.isEmpty())

        submit(vm, "error")
        assertEquals(LoadState.Loading, result.refresh)
        errorResponse.complete(Unit)
        runCurrent()
        assertEquals(LoadState.Error(failure), result.refresh)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun aNewViewModelStartsEmptyWithoutRepeatingThePreviousSearch() = runTest {
        val original = viewModel()
        collectProducts(original)
        submit(original, "Asus")
        repository.requests.single().complete(1)
        runCurrent()
        original.onEvent(SearchUiEvent.QueryChanged("Apple draft"))
        store.clear()
        runCurrent()

        val fresh = viewModel()
        val result = collectProducts(fresh)
        runCurrent()
        assertEquals(SearchUiState(), fresh.searchUiState.value)
        assertTrue(result.items.isEmpty())
        assertEquals(1, repository.requests.size)
        assertEquals("Asus", repository.requests.single().query)
    }

    @Test
    fun rejectsLateResultsIncludingAtoBtoA() = runTest {
        val vm = viewModel()
        val result = collectProducts(vm)
        submit(vm, "Asus")
        val oldAsus = repository.requests.last()
        oldAsus.complete(1)
        runCurrent()
        assertEquals(listOf(1L), result.ids)
        submit(vm, "Apple")
        val apple = repository.requests.last()
        assertTrue(result.items.isEmpty())
        submit(vm, "Asus")
        val currentAsus = repository.requests.last()
        currentAsus.complete(3)
        runCurrent()
        oldAsus.complete(100)
        apple.complete(200)
        favorites.confirm(setOf(3))
        runCurrent()
        assertEquals(listOf(3L), result.ids)
        assertEquals(FavoriteProductStatus.Favorited, result.items.single().favoriteStatus)
        assertTrue(oldAsus.cancelled)
        assertTrue(apple.cancelled)
    }

    @Test
    fun rapidAtoBtoADoesNotGetLostThroughStateFlowConflation() = runTest {
        val vm = viewModel()
        val result = collectProducts(vm)
        submit(vm, "Asus")
        repository.requests.last().complete(1)
        runCurrent()
        vm.onEvent(SearchUiEvent.QueryChanged("Apple"))
        vm.onEvent(SearchUiEvent.SearchSubmitted)
        vm.onEvent(SearchUiEvent.QueryChanged("Asus"))
        vm.onEvent(SearchUiEvent.SearchSubmitted)
        runCurrent()
        assertEquals(listOf("Asus", "Asus"), repository.requests.map { it.query })
        assertTrue(repository.requests.first().cancelled)
        repository.requests.last().complete(2)
        runCurrent()
        assertEquals(listOf(2L), result.ids)
    }

    @Test
    fun aSecondCollectorReusesTheCurrentGeneration() = runTest {
        val vm = viewModel()
        val first = collectProducts(vm)
        submit(vm, "Asus")
        repository.requests.single().complete(1)
        runCurrent()
        val second = collectProducts(vm)
        runCurrent()
        assertEquals(first.ids, second.ids)
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun recreatedUiReusesCacheWithoutRestartingTheSearch() = runTest {
        val vm = viewModel()
        val first = collectProducts(vm)
        submit(vm, "Asus")
        val oldRequest = repository.requests.single()
        oldRequest.complete(1)
        runCurrent()
        first.job.cancel()
        runCurrent()
        advanceTimeBy(60_000.toDuration(DurationUnit.MILLISECONDS))
        val second = collectProducts(vm)
        runCurrent()
        assertEquals(listOf(1L), second.ids)
        assertEquals(1, repository.requests.size)
        assertFalse(oldRequest.cancelled)
    }

    @Test
    fun aNewQueryDuringSubscriptionGapCannotReplayOldProducts() = runTest {
        val vm = viewModel()
        val first = collectProducts(vm)
        submit(vm, "Asus")
        repository.requests.single().complete(1)
        runCurrent()
        first.job.cancel()
        runCurrent()
        vm.onEvent(SearchUiEvent.QueryChanged("Apple"))
        vm.onEvent(SearchUiEvent.SearchSubmitted)
        val second = collectProducts(vm)
        runCurrent()
        assertTrue(second.items.isEmpty())
        repository.requests.last().complete(2)
        runCurrent()
        assertEquals(listOf(2L), second.ids)
    }

    @Test
    fun favoritesBlockConcurrentWritesUntilDatabaseObservationConfirmsTheChange() = runTest {
        val vm = viewModel()
        val result = collectProducts(vm)
        submit(vm, "Asus")
        repository.requests.single().complete(1, 2)
        runCurrent()
        favorites.autoConfirm = false
        val gate = CompletableDeferred<Unit>()
        favorites.writeGate = gate
        vm.onEvent(SearchUiEvent.FavoriteClicked(1))
        vm.onEvent(SearchUiEvent.FavoriteClicked(2))
        vm.onEvent(SearchUiEvent.FavoriteClicked(1))
        runCurrent()
        assertEquals(listOf(1L to true), favorites.writes)
        assertEquals(FavoriteProductStatus.InProgress, result.items.first().favoriteStatus)
        assertEquals(FavoriteProductStatus.Unfavorited, result.items.last().favoriteStatus)
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, favorites.writes.size)
        assertEquals(FavoriteProductStatus.InProgress, result.items.first().favoriteStatus)
        vm.onEvent(SearchUiEvent.FavoriteClicked(1))
        vm.onEvent(SearchUiEvent.FavoriteClicked(2))
        runCurrent()
        assertEquals(listOf(1L to true), favorites.writes)
        favorites.confirm(setOf(2))
        runCurrent()
        assertEquals(FavoriteProductStatus.InProgress, result.items.first().favoriteStatus)
        favorites.confirm(setOf(1))
        runCurrent()
        assertEquals(FavoriteProductStatus.Favorited, result.items.first().favoriteStatus)
        assertEquals(FavoriteProductStatus.Unfavorited, result.items.last().favoriteStatus)

        vm.onEvent(SearchUiEvent.FavoriteClicked(1))
        runCurrent()
        assertEquals(listOf(1L to true, 1L to false), favorites.writes)
        assertEquals(FavoriteProductStatus.InProgress, result.items.first().favoriteStatus)
        vm.onEvent(SearchUiEvent.FavoriteClicked(2))
        runCurrent()
        assertEquals(2, favorites.writes.size)
        favorites.confirm(emptySet())
        runCurrent()
        assertEquals(FavoriteProductStatus.Unfavorited, result.items.first().favoriteStatus)

        vm.onEvent(SearchUiEvent.FavoriteClicked(2))
        runCurrent()
        assertEquals(listOf(1L to true, 1L to false, 2L to true), favorites.writes)
        favorites.confirm(setOf(2))
        runCurrent()
        assertEquals(FavoriteProductStatus.Favorited, result.items.last().favoriteStatus)
        assertEquals(1, favorites.observationCount)
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun favoritesAreUnavailableUntilTheFirstDatabaseObservation() = runTest {
        favorites.observationGate = CompletableDeferred()
        val vm = viewModel()
        val result = collectProducts(vm)
        submit(vm, "Asus")
        repository.requests.single().complete(1)
        runCurrent()
        assertEquals(FavoriteProductStatus.Unavailable, result.items.single().favoriteStatus)
        vm.onEvent(SearchUiEvent.FavoriteClicked(1))
        runCurrent()
        assertTrue(favorites.writes.isEmpty())
        favorites.observationGate?.complete(Unit)
        runCurrent()
        assertEquals(FavoriteProductStatus.Unfavorited, result.items.single().favoriteStatus)
    }

    @Test
    fun earlyDatabaseConfirmationDoesNotFinishAnActiveWrite() = runTest {
        val vm = viewModel()
        val result = collectProducts(vm)
        submit(vm, "Asus")
        repository.requests.single().complete(1)
        runCurrent()
        favorites.autoConfirm = false
        favorites.writeGate = CompletableDeferred()
        vm.onEvent(SearchUiEvent.FavoriteClicked(1))
        runCurrent()
        favorites.confirm(setOf(1))
        runCurrent()
        assertEquals(FavoriteProductStatus.InProgress, result.items.single().favoriteStatus)
        vm.onEvent(SearchUiEvent.FavoriteClicked(2))
        runCurrent()
        assertEquals(1, favorites.writes.size)
        favorites.writeGate?.complete(Unit)
        runCurrent()
        assertEquals(FavoriteProductStatus.Favorited, result.items.single().favoriteStatus)
    }

    @Test
    fun readFailureDuringAWriteHidesRowsButKeepsOtherClicksBlocked() = runTest {
        val vm = viewModel()
        val effects = collectEffects(vm)
        val result = collectProducts(vm)
        submit(vm, "Asus")
        repository.requests.single().complete(1)
        runCurrent()
        favorites.autoConfirm = false
        favorites.writeGate = CompletableDeferred()
        vm.onEvent(SearchUiEvent.FavoriteClicked(1))
        runCurrent()
        favorites.failRead()
        runCurrent()
        assertEquals(FavoriteProductStatus.Unavailable, result.items.single().favoriteStatus)
        vm.onEvent(SearchUiEvent.FavoriteClicked(2))
        runCurrent()
        assertEquals(1, favorites.writes.size)
        favorites.writeGate?.complete(Unit)
        runCurrent()
        assertEquals(FavoriteProductStatus.Unavailable, result.items.single().favoriteStatus)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun readFailureReleasesTheConfirmationWaitWithoutAnErrorEffect() = runTest {
        val vm = viewModel()
        val effects = collectEffects(vm)
        val result = collectProducts(vm)
        submit(vm, "Asus")
        repository.requests.single().complete(1)
        runCurrent()
        favorites.autoConfirm = false
        vm.onEvent(SearchUiEvent.FavoriteClicked(1))
        runCurrent()
        assertEquals(FavoriteProductStatus.InProgress, result.items.single().favoriteStatus)
        result.job.cancel()
        advanceTimeBy(5_001.toDuration(DurationUnit.MILLISECONDS))
        runCurrent()
        assertEquals(1, favorites.activeObservations)

        favorites.failRead()
        runCurrent()
        assertEquals(0, favorites.activeObservations)
        assertTrue(effects.isEmpty())

        // With no remaining confirmation subscriber, WhileSubscribed can restart observation.
        advanceTimeBy(5_001.toDuration(DurationUnit.MILLISECONDS))
        runCurrent()
        val restored = collectProducts(vm)
        runCurrent()
        assertEquals(2, favorites.observationCount)
        assertEquals(FavoriteProductStatus.Unavailable, restored.items.single().favoriteStatus)
    }

    @Test
    fun clearingTheViewModelCancelsTheConfirmationWaitWithoutAnErrorEffect() = runTest {
        val vm = viewModel()
        val effects = collectEffects(vm)
        val result = collectProducts(vm)
        submit(vm, "Asus")
        repository.requests.single().complete(1)
        runCurrent()
        favorites.autoConfirm = false
        vm.onEvent(SearchUiEvent.FavoriteClicked(1))
        runCurrent()
        assertEquals(FavoriteProductStatus.InProgress, result.items.single().favoriteStatus)

        result.job.cancel()
        store.clear()
        runCurrent()
        assertEquals(0, favorites.activeObservations)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun aClickFromAnOldRowUsesTheLatestDatabaseState() = runTest {
        val vm = viewModel()
        val result = collectProducts(vm)
        submit(vm, "Asus")
        repository.requests.single().complete(1)
        runCurrent()
        val oldRow = result.items.single()
        favorites.confirm(setOf(1))
        runCurrent()
        assertEquals(FavoriteProductStatus.Unfavorited, oldRow.favoriteStatus)
        vm.onEvent(SearchUiEvent.FavoriteClicked(oldRow.productId))
        runCurrent()
        assertEquals(listOf(1L to false), favorites.writes)
        assertEquals(FavoriteProductStatus.Unfavorited, result.items.single().favoriteStatus)
    }

    @Test
    fun favoriteUpdatesFollowNewSearchWithoutRestoringOldProducts() = runTest {
        val vm = viewModel()
        val result = collectProducts(vm)
        submit(vm, "Asus")
        repository.requests.single().complete(1)
        runCurrent()
        favorites.writeGate = CompletableDeferred()
        vm.onEvent(SearchUiEvent.FavoriteClicked(1))
        runCurrent()
        submit(vm, "Apple")
        repository.requests.last().complete(2)
        runCurrent()
        favorites.writeGate?.complete(Unit)
        runCurrent()
        assertEquals(listOf(2L), result.ids)
        assertEquals(FavoriteProductStatus.Unfavorited, result.items.single().favoriteStatus)
    }

    @Test
    fun readFailureHidesFavoritesAndSearchStillWorks() = runTest {
        val vm = viewModel()
        val result = collectProducts(vm)
        submit(vm, "Asus")
        repository.requests.single().complete(1)
        runCurrent()
        favorites.failRead()
        runCurrent()
        assertEquals(FavoriteProductStatus.Unavailable, result.items.single().favoriteStatus)
        vm.onEvent(SearchUiEvent.FavoriteClicked(1))
        runCurrent()
        assertTrue(favorites.writes.isEmpty())
        submit(vm, "Apple")
        repository.requests.last().complete(2)
        runCurrent()
        assertEquals(listOf(2L), result.ids)
        assertEquals(FavoriteProductStatus.Unavailable, result.items.single().favoriteStatus)
    }

    @Test
    fun writeFailureKeepsObservedFavoritesEmitsAnEffectAndAllowsRetry() = runTest {
        val vm = viewModel()
        val effects = collectEffects(vm)
        val result = collectProducts(vm)
        submit(vm, "Asus")
        repository.requests.single().complete(1)
        favorites.confirm(setOf(1))
        runCurrent()
        favorites.writeFailure = IllegalStateException("database unavailable")
        vm.onEvent(SearchUiEvent.FavoriteClicked(1))
        runCurrent()
        assertEquals(FavoriteProductStatus.Favorited, result.items.single().favoriteStatus)
        assertEquals(listOf(SearchUiEffect.ShowFavoriteUpdateError), effects)
        favorites.writeFailure = null
        vm.onEvent(SearchUiEvent.FavoriteClicked(1))
        runCurrent()
        assertEquals(listOf(1L to false, 1L to false), favorites.writes)
        assertEquals(FavoriteProductStatus.Unfavorited, result.items.single().favoriteStatus)
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun cancellationDoesNotHideFavoritesOrLeaveUpdatingSet() = runTest {
        val vm = viewModel()
        val effects = collectEffects(vm)
        val result = collectProducts(vm)
        submit(vm, "Asus")
        repository.requests.single().complete(1)
        runCurrent()
        favorites.writeFailure = CancellationException("cancelled")
        vm.onEvent(SearchUiEvent.FavoriteClicked(1))
        runCurrent()
        assertEquals(FavoriteProductStatus.Unfavorited, result.items.single().favoriteStatus)
        assertTrue(effects.isEmpty())
        favorites.writeFailure = null
        vm.onEvent(SearchUiEvent.FavoriteClicked(1))
        runCurrent()
        assertEquals(FavoriteProductStatus.Favorited, result.items.single().favoriteStatus)
        assertEquals(2, favorites.writes.size)
    }

    @Test
    fun favoritesShareOneObservationAndStopAfterTheSubscriptionTimeout() = runTest {
        val vm = viewModel()
        runCurrent()
        assertEquals(0, favorites.observationCount)
        val first = collectProducts(vm)
        val second = collectProducts(vm)
        runCurrent()
        assertEquals(1, favorites.observationCount)
        assertEquals(1, favorites.activeObservations)
        first.job.cancel()
        runCurrent()
        assertEquals(1, favorites.activeObservations)
        second.job.cancel()
        runCurrent()
        advanceTimeBy(4_999.toDuration(DurationUnit.MILLISECONDS))
        runCurrent()
        assertEquals(1, favorites.activeObservations)
        advanceTimeBy(1.toDuration(DurationUnit.MILLISECONDS))
        runCurrent()
        assertEquals(0, favorites.activeObservations)
        favorites.confirm(setOf(1))
        val restored = collectProducts(vm)
        submit(vm, "Asus")
        repository.requests.single().complete(1)
        runCurrent()
        assertEquals(2, favorites.observationCount)
        assertEquals(1, favorites.activeObservations)
        assertEquals(FavoriteProductStatus.Favorited, restored.items.single().favoriteStatus)
    }

    @Test
    fun errorEffectsAreDeliveredOnlyToCurrentCollectorsWithoutReplay() = runTest {
        val vm = viewModel()
        collectProducts(vm)
        runCurrent()
        favorites.writeFailure = IllegalStateException("database unavailable")
        vm.onEvent(SearchUiEvent.FavoriteClicked(1))
        runCurrent()
        val first = collectEffects(vm)
        runCurrent()
        assertTrue(first.isEmpty())
        vm.onEvent(SearchUiEvent.FavoriteClicked(1))
        runCurrent()
        assertEquals(listOf(SearchUiEffect.ShowFavoriteUpdateError), first)
        val second = collectEffects(vm)
        runCurrent()
        assertTrue(second.isEmpty())
    }

    @Test
    fun aSlowEffectCollectorDoesNotKeepTheWriteGuardSet() = runTest {
        val vm = viewModel()
        collectProducts(vm)
        val effectGate = CompletableDeferred<Unit>()
        val effects = mutableListOf<SearchUiEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.effects.collect {
                effects += it
                effectGate.await()
            }
        }
        runCurrent()
        favorites.writeFailure = IllegalStateException("database unavailable")
        repeat(3) {
            vm.onEvent(SearchUiEvent.FavoriteClicked(1))
            runCurrent()
        }
        assertEquals(3, favorites.writes.size)
        assertEquals(listOf(SearchUiEffect.ShowFavoriteUpdateError), effects)
        effectGate.complete(Unit)
        runCurrent()
        assertEquals(2, effects.size)
    }

    @Test
    fun mapsProductDetailsWithoutFormattingPriceOrRating() = runTest {
        val vm = viewModel()
        val result = collectProducts(vm)
        submit(vm, "Asus")
        repository.requests.single().pages.emit(PagingData.from(listOf(product(1))))
        runCurrent()
        val row = result.items.single()
        assertEquals(BigDecimal("24.99"), row.salesPriceIncVat)
        assertEquals(BigDecimal("29.99"), row.listPriceIncVat)
        assertEquals(9.2, row.reviewAverage, 0.0)
        assertEquals(10, row.reviewCount)
        assertEquals("Product 1", row.productName)
        assertEquals("https://example.com/1.png", row.productImage)
    }

    @Test
    fun paginationStartsAtOneAndLoadsTheNextPage() = runTest {
        val loadedPages = mutableListOf<Int>()
        val pagedRepository = object : ProductRepository {
            override fun search(query: String): Flow<PagingData<Product>> = Pager(
                PagingConfig(
                    pageSize = 2,
                    initialLoadSize = 2,
                    prefetchDistance = 1,
                    enablePlaceholders = false
                )
            ) { TestPagingSource(loadedPages) }.flow
        }
        val vm = viewModel(products = pagedRepository)
        val result = collectProducts(vm)
        submit(vm, "Asus")
        assertEquals(listOf(1L, 2L), result.ids)
        val snapshot = vm.products.asSnapshot { scrollTo(3) }
        assertEquals(listOf(1L, 2L, 3L, 4L), snapshot.map { it.productId })
        assertEquals(listOf(1, 2), loadedPages)
    }

    @Test
    fun lateNonCooperativePageCannotReplaceTheNewSearch() = runTest {
        val latePage = CompletableDeferred<Unit>()
        var loadStarted = false
        val pagedRepository = object : ProductRepository {
            override fun search(query: String): Flow<PagingData<Product>> = Pager(
                PagingConfig(pageSize = 2, initialLoadSize = 2, enablePlaceholders = false)
            ) {
                object : PagingSource<Int, Product>() {
                    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Product> {
                        if (query == "Asus") {
                            loadStarted = true
                            withContext(NonCancellable) { latePage.await() }
                        }
                        return LoadResult.Page(
                            listOf(
                                product(
                                    if (query == "Asus") {
                                        1
                                    } else {
                                        2
                                    }
                                )
                            ),
                            null,
                            null
                        )
                    }

                    override fun getRefreshKey(state: PagingState<Int, Product>): Int? = null
                }
            }.flow
        }
        val vm = viewModel(products = pagedRepository)
        val result = collectProducts(vm)
        submit(vm, "Asus")
        assertTrue(loadStarted)
        vm.onEvent(SearchUiEvent.QueryChanged("Apple"))
        vm.onEvent(SearchUiEvent.SearchSubmitted)
        runCurrent()
        latePage.complete(Unit)
        runCurrent()
        assertEquals(listOf(2L), result.ids)
    }

    private fun viewModel(products: ProductRepository = repository): SearchViewModel =
        SearchViewModel(products, favorites).also {
            store.put("search-${viewModelCount++}", it)
        }

    private fun TestScope.submit(vm: SearchViewModel, query: String) {
        vm.onEvent(SearchUiEvent.QueryChanged(query))
        vm.onEvent(SearchUiEvent.SearchSubmitted)
        runCurrent()
    }

    private fun TestScope.collectProducts(vm: SearchViewModel): CollectedProducts {
        val presenter = object : PagingDataPresenter<SearchProductUiModel>(Dispatchers.Main) {
            override suspend fun presentPagingDataEvent(
                event: PagingDataEvent<SearchProductUiModel>
            ) = Unit
        }
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            launch { vm.searchUiState.collect {} }
            vm.products.collectLatest { presenter.collectFrom(it) }
        }
        return CollectedProducts(presenter, job)
    }

    private fun TestScope.collectEffects(vm: SearchViewModel): MutableList<SearchUiEffect> {
        val effects = mutableListOf<SearchUiEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.effects.collect { effects += it }
        }
        return effects
    }

    private class CollectedProducts(
        private val presenter: PagingDataPresenter<SearchProductUiModel>,
        val job: kotlinx.coroutines.Job
    ) {
        val items: List<SearchProductUiModel> get() = presenter.snapshot().items
        val ids: List<Long> get() = items.map { it.productId }
        val refresh: LoadState? get() = presenter.loadStateFlow.value?.refresh
    }

    private class FakeProducts : ProductRepository {
        val requests = mutableListOf<Request>()

        override fun search(query: String): Flow<PagingData<Product>> = flow {
            val request = Request(query)
            requests += request
            try {
                emitAll(request.pages)
            } finally {
                request.cancelled = true
            }
        }

        class Request(val query: String) {
            val pages = MutableSharedFlow<PagingData<Product>>(replay = 1)
            var cancelled = false

            fun complete(vararg ids: Long) {
                pages.tryEmit(PagingData.from(ids.map { product(it) }))
            }
        }
    }

    private class FakeFavorites : FavoritesRepository {
        private val ids = MutableStateFlow(Result.success(emptySet<Long>()))
        val writes = mutableListOf<Pair<Long, Boolean>>()
        var writeGate: CompletableDeferred<Unit>? = null
        var observationGate: CompletableDeferred<Unit>? = null
        var writeFailure: Exception? = null
        var autoConfirm = true
        var observationCount = 0
        var activeObservations = 0

        override fun observeFavoriteIds(): Flow<Set<Long>> = flow {
            observationCount++
            activeObservations++
            try {
                observationGate?.await()
                emitAll(ids.map { it.getOrThrow() })
            } finally {
                activeObservations--
            }
        }

        override suspend fun setFavorite(productId: Long, isFavorite: Boolean) {
            writes += productId to isFavorite
            writeGate?.await()
            writeFailure?.let { throw it }
            if (autoConfirm) {
                confirm(
                    if (isFavorite) {
                        ids.value.getOrThrow() + productId
                    } else {
                        ids.value.getOrThrow() -
                            productId
                    }
                )
            }
        }

        fun confirm(favorites: Set<Long>) {
            ids.value = Result.success(favorites)
        }

        fun failRead() {
            ids.value = Result.failure(IllegalStateException("database unavailable"))
        }
    }

    private class TestPagingSource(private val loadedPages: MutableList<Int>) :
        PagingSource<Int, Product>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Product> {
            val page = params.key ?: 1
            loadedPages += page
            val firstId = (page * 2 - 1).toLong()
            return LoadResult.Page(
                listOf(product(firstId), product(firstId + 1)),
                if (page == 1) null else page - 1,
                if (page == 1) 2 else null
            )
        }

        override fun getRefreshKey(state: PagingState<Int, Product>): Int? = null
    }

    private companion object {
        fun product(id: Long): Product = Product(
            productId = id,
            productName = "Product $id",
            salesPriceIncVat = BigDecimal("24.99"),
            productImage = "https://example.com/$id.png",
            reviewSummary = ReviewSummary(9.2, 10),
            listPriceIncVat = BigDecimal("29.99")
        )
    }
}
