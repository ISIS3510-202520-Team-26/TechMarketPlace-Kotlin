package com.techmarketplace.data.storage

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.techmarketplace.data.remote.dto.ListingDetailDto
import java.io.File
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingDetailCacheStoreTest {

    @Test
    fun save_respectsLruOrder_andLimit() = runTest {
        var now = 0L
        val store = this.createStore(cacheLimit = 2, ttlMillis = 10_000L, clock = { now })

        store.save(detail("1"))
        now += 100
        store.save(detail("2"))
        now += 100
        store.save(detail("3"))

        val snapshot = store.snapshot()
        assertEquals(listOf("3", "2"), snapshot.map { it.listingId })
        assertEquals(2, snapshot.size)
    }

    @Test
    fun get_promotesEntry_andReturnsDetail() = runTest {
        var now = 0L
        val store = this.createStore(cacheLimit = 3, ttlMillis = 10_000L, clock = { now })
        store.save(detail("a", title = "Alpha"))
        now += 100
        store.save(detail("b", title = "Beta"))

        val cached = store.get("a")

        assertEquals("a", cached?.listingId)
        assertEquals("Alpha", cached?.detail?.title)
        val snapshot = store.snapshot()
        assertEquals(listOf("a", "b"), snapshot.map { it.listingId })
    }

    @Test
    fun get_removesExpiredEntries() = runTest {
        var now = 0L
        val ttl = 500L
        val store = this.createStore(cacheLimit = 2, ttlMillis = ttl, clock = { now })
        store.save(detail("1"))

        now += ttl + 1
        val cached = store.get("1")
        assertNull(cached)
        assertTrue(store.snapshot().isEmpty())
    }
}

private fun detail(id: String, title: String = "Listing $id") = ListingDetailDto(
    id = id,
    title = title,
    description = null,
    categoryId = "cat",
    brandId = null,
    priceCents = 1000,
    currency = "COP",
    condition = "used",
    quantity = 1
)

private fun TestScope.createStore(
    cacheLimit: Int,
    ttlMillis: Long,
    clock: () -> Long
): ListingDetailCacheStore {
    val file = File.createTempFile("listing-detail-cache", ".preferences_pb").apply {
        deleteOnExit()
    }
    val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
    return ListingDetailCacheStore.fromDataStore(
        dataStore = dataStore,
        cacheLimit = cacheLimit,
        ttlMillis = ttlMillis,
        clock = clock
    )
}
