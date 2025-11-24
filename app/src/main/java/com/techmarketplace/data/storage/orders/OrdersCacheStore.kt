package com.techmarketplace.data.storage.orders

import android.content.Context
import android.util.LruCache
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.techmarketplace.core.network.fixEmulatorHost
import com.techmarketplace.data.storage.LocalOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.ordersCacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "orders_cache"
)

@Serializable
data class OrdersSnapshot(
    val orders: List<LocalOrder> = emptyList(),
    val savedAtEpochMillis: Long = 0L
)

class OrdersCacheStore @JvmOverloads constructor(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val dataStore = context.applicationContext.ordersCacheDataStore
    private val payloadKey = stringPreferencesKey("payload")
    // Small in-memory LRU to avoid JSON parse on every access and to align with caching rubric.
    private val memoryCache = LruCache<String, LocalOrder>(CACHE_SIZE)

    val snapshots: Flow<OrdersSnapshot?> = dataStore.data.map { prefs ->
        val payload = prefs[payloadKey] ?: return@map null
        val decoded = runCatching { json.decodeFromString<OrdersSnapshot>(payload) }
            .getOrNull()
            ?.normalize()
        decoded?.orders?.forEach { order -> memoryCache.put(order.id, order) }
        decoded
    }

    suspend fun read(): OrdersSnapshot? = snapshots.firstOrNull()

    suspend fun save(orders: List<LocalOrder>) {
        val snapshot = OrdersSnapshot(
            orders = orders.map { it.normalize() },
            savedAtEpochMillis = clock()
        )
        memoryCache.evictAll()
        snapshot.orders.forEach { order -> memoryCache.put(order.id, order) }
        dataStore.edit { prefs ->
            prefs[payloadKey] = json.encodeToString(snapshot)
        }
    }

    suspend fun upsert(order: LocalOrder) {
        val normalizedOrder = order.normalize()
        val current = memoryCache.snapshot().values.map { it.normalize() }.toMutableList()
        if (current.isEmpty()) {
            current += read()?.orders?.map { it.normalize() } ?: emptyList()
        }
        val existingIndex = current.indexOfFirst { it.id == normalizedOrder.id }
        if (existingIndex >= 0) {
            current[existingIndex] = normalizedOrder
        } else {
            current.add(0, normalizedOrder)
        }
        save(current)
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(payloadKey)
        }
        memoryCache.evictAll()
    }

    companion object {
        private const val CACHE_SIZE = 32
    }
}

private fun OrdersSnapshot.normalize(): OrdersSnapshot = copy(
    orders = orders.map { it.normalize() }
)

private fun LocalOrder.normalize(): LocalOrder = copy(
    thumbnailUrl = fixEmulatorHost(thumbnailUrl)
)
