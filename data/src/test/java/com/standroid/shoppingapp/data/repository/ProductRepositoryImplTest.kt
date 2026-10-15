package com.standroid.shoppingapp.data.repository

import androidx.paging.testing.asSnapshot
import com.standroid.shoppingapp.data.remote.data.searchJson
import com.standroid.shoppingapp.data.remote.server.FakeProductServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ProductRepositoryImplTest {
    @Test fun blankQueriesNeverReachTheNetwork() = runTest {
        FakeProductServer().use { fakeProductServer ->
            assertTrue(
                ProductRepositoryImpl(
                    fakeProductServer.api
                ).search("  \t\n ").asSnapshot().isEmpty()
            )
            assertEquals(0, fakeProductServer.server.requestCount)
        }
    }

    @Test fun trimsQueriesAndCreatesIndependentPagingGenerations() = runTest {
        FakeProductServer().use { server ->
            repeat(3) { server.enqueueJson(searchJson(pageCount = 1, total = 1)) }
            val repository = ProductRepositoryImpl(server.api)
            val apple = repository.search("  Apple  ")
            assertEquals(1, apple.asSnapshot().size)
            assertEquals(1, repository.search("Asus").asSnapshot().size)
            assertEquals(1, apple.asSnapshot().size)
            val queries = List(3) {
                requireNotNull(server.server.takeRequest(5, TimeUnit.SECONDS))
                    .requestUrl?.queryParameter("que")
            }
            assertEquals(listOf("Apple", "Asus", "Apple"), queries)
        }
    }
}
