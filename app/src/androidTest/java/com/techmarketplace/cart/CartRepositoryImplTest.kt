package com.techmarketplace.cart

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.techmarketplace.data.remote.api.CartFetchResult
import com.techmarketplace.data.remote.api.CartRemoteDataSource
import com.techmarketplace.data.remote.api.CartRemoteItem
import com.techmarketplace.data.storage.CartPreferences
import com.techmarketplace.data.storage.cart.CartLocalDataSource
import com.techmarketplace.data.storage.dao.CartDatabase
import com.techmarketplace.data.storage.dao.CartTypeConverters
import com.techmarketplace.domain.cart.CartItemUpdate
import com.techmarketplace.domain.cart.CartSyncOperation
import com.techmarketplace.domain.cart.CartVariantDetail
import com.techmarketplace.data.repository.cart.CartRepositoryImpl
import com.techmarketplace.data.storage.cart.CartLocalDataSource.Companion.buildCartItemId
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CartRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var database: CartDatabase
    private lateinit var preferences: CartPreferences
    private lateinit var local: CartLocalDataSource
    private lateinit var remote: FakeCartRemoteDataSource
    private lateinit var connectivity: MutableStateFlow<Boolean>
    private lateinit var dispatcher: StandardTestDispatcher
    private lateinit var scope: TestScope
    private lateinit var repository: CartRepositoryImpl
    private var now: Long = 0L

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CartDatabase::class.java)
            .allowMainThreadQueries()
            .addTypeConverter(CartTypeConverters)
            .build()
        preferences = CartPreferences(context)
        dispatcher = StandardTestDispatcher()
        scope = TestScope(dispatcher)
        local = CartLocalDataSource(database.cartDao(), preferences, dispatcher = dispatcher) { now }
        remote = FakeCartRemoteDataSource()
        connectivity = MutableStateFlow(false)
        repository = CartRepositoryImpl(local, remote, connectivity, scope, dispatcher)
    }

    @After
    fun tearDown() {
        database.close()
        // Clean up preferences file between tests
        val storeFile = File(context.filesDir, "datastore/cart_metadata.preferences_pb")
        if (storeFile.exists()) storeFile.delete()
    }

    @Test
    fun ttlEvictionRemovesExpiredItems() = scope.runTest {
        val update = CartItemUpdate(
            productId = "product-1",
            title = "Laptop",
            priceCents = 100_00,
            currency = "usd",
            quantity = 1,
            variantDetails = listOf(CartVariantDetail("Color", "Space Gray"))
        )
        now = 0L
        local.upsert(update, ttlOverride = 1_000L)
        now = 5_000L
        val evicted = local.evictExpired()
        assertEquals(1, evicted)
        val active = local.getActive()
        assertTrue(active.isEmpty())
    }

    @Test
    fun offlineOperationsAreQueued() = scope.runTest {
        val variants = listOf(CartVariantDetail("Storage", "1TB"))
        val update = CartItemUpdate(
            productId = "prod-1",
            title = "Phone",
            priceCents = 80_00,
            currency = "usd",
            quantity = 2,
            variantDetails = variants
        )

        repository.addOrUpdate(update)
        advanceUntilIdle()

        assertTrue(remote.upserted.isEmpty())
        val pending = local.pendingOperations()
        assertEquals(1, pending.size)
        assertEquals(CartSyncOperation.ADD, pending.first().pendingOperation)

        val cartItemId = buildCartItemId(update.productId, variants)
        repository.remove(cartItemId, variants)
        advanceUntilIdle()

        val pendingAfterRemove = local.pendingOperations()
        assertTrue(pendingAfterRemove.any { it.pendingOperation == CartSyncOperation.REMOVE })
    }

    @Test
    fun loginFlushesPendingOperations() = scope.runTest {
        val variants = listOf(CartVariantDetail("Edition", "Pro"))
        val update = CartItemUpdate(
            productId = "prod-2",
            title = "Headphones",
            priceCents = 50_00,
            currency = "usd",
            quantity = 1,
            variantDetails = variants
        )

        repository.addOrUpdate(update)
        advanceUntilIdle()
        assertTrue(remote.upserted.isEmpty())

        connectivity.value = true
        repository.onLogin()
        advanceUntilIdle()

        assertEquals(1, remote.upserted.size)
        val pending = local.pendingOperations()
        assertTrue(pending.isEmpty())
    }

    private class FakeCartRemoteDataSource : CartRemoteDataSource {
        val upserted = mutableListOf<CartRemoteItem>()
        val removed = mutableListOf<String>()

        override suspend fun fetchCart(): CartFetchResult = CartFetchResult(upserted.toList())

        override suspend fun upsertItem(item: CartRemoteItem): CartRemoteItem {
            upserted.removeAll { it.cartItemId == item.cartItemId }
            upserted.add(item)
            return item
        }

        override suspend fun removeItem(cartItemId: String) {
            removed.add(cartItemId)
            upserted.removeAll { it.cartItemId == cartItemId }
        }
    }
}
