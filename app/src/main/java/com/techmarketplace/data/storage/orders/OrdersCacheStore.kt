package com.techmarketplace.data.storage.orders

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    val snapshots: Flow<OrdersSnapshot?> = dataStore.data.map { prefs ->
        val payload = prefs[payloadKey] ?: return@map null
        runCatching { json.decodeFromString<OrdersSnapshot>(payload) }.getOrNull()
    }

    suspend fun read(): OrdersSnapshot? = snapshots.firstOrNull()

    suspend fun save(orders: List<LocalOrder>) {
        val snapshot = OrdersSnapshot(orders = orders, savedAtEpochMillis = clock())
        dataStore.edit { prefs ->
            prefs[payloadKey] = json.encodeToString(snapshot)
        }
    }

    suspend fun upsert(order: LocalOrder) {
        val current = read()?.orders?.toMutableList() ?: mutableListOf()
        val existingIndex = current.indexOfFirst { it.id == order.id }
        if (existingIndex >= 0) {
            current[existingIndex] = order
        } else {
            current.add(0, order)
        }
        save(current)
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(payloadKey)
        }
    }
}
