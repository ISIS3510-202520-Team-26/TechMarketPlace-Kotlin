package com.techmarketplace.cart

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.techmarketplace.data.repository.cart.CartRepositoryImpl
import com.techmarketplace.data.storage.CartPreferences
import com.techmarketplace.data.storage.cart.CartLocalDataSource
import com.techmarketplace.data.storage.dao.CartDatabase
import com.techmarketplace.data.storage.dao.CartTypeConverters
import com.techmarketplace.domain.cart.CartItemUpdate
import com.techmarketplace.domain.cart.CartVariantDetail
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CartRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var database: CartDatabase
    private lateinit var preferences: CartPreferences
    private lateinit var local: CartLocalDataSource
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
        connectivity = MutableStateFlow(true)
        repository = CartRepositoryImpl(local, connectivity, scope, dispatcher)
    }

    @After
    fun tearDown() {
        database.close()
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
    fun addOrUpdateStoresItemsLocally() = scope.runTest {
        val update = CartItemUpdate(
            productId = "prod-1",
            title = "Phone",
            priceCents = 80_00,
            currency = "usd",
            quantity = 2,
            variantDetails = listOf(CartVariantDetail("Storage", "1TB"))
        )

        repository.addOrUpdate(update)
        advanceUntilIdle()

        val items = local.getActive()
        assertEquals(1, items.size)
        assertEquals(update.productId, items.first().productId)
        assertEquals(update.quantity, items.first().quantity)
    }
}
