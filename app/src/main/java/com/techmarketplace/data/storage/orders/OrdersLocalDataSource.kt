package com.techmarketplace.data.storage.orders

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.techmarketplace.data.storage.LocalOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.ordersStore: DataStore<Preferences> by preferencesDataStore(name = "orders_store")

class OrdersLocalDataSource internal constructor(
    private val dataStore: DataStore<Preferences>
) {

    constructor(context: Context) : this(context.applicationContext.ordersStore)

    private val ordersKey = stringPreferencesKey("orders_json")
    private val lastSyncKey = longPreferencesKey("last_sync_epoch")

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val orders: Flow<List<LocalOrder>> = dataStore.data
        .catch { cause ->
            if (cause is IOException) {
                emit(emptyPreferences())
            } else {
                throw cause
            }
        }
        .map { prefs ->
            val raw = prefs[ordersKey]
            if (raw.isNullOrBlank()) {
                emptyList()
            } else {
                runCatching { json.decodeFromString<List<LocalOrder>>(raw) }
                    .getOrElse { emptyList() }
            }
        }

    val lastSyncEpochMillis: Flow<Long?> = dataStore.data
        .catch { cause ->
            if (cause is IOException) {
                emit(emptyPreferences())
            } else {
                throw cause
            }
        }
        .map { prefs -> prefs[lastSyncKey] }

    suspend fun replaceAll(orders: List<LocalOrder>, lastSync: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            val sorted = orders.sortedByDescending { it.createdAtEpochMillis ?: Long.MIN_VALUE }
            prefs[ordersKey] = json.encodeToString(sorted)
            prefs[lastSyncKey] = lastSync
        }
    }

    suspend fun upsert(order: LocalOrder, lastSync: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            val current = prefs[ordersKey]?.let { stored ->
                try {
                    json.decodeFromString<List<LocalOrder>>(stored)
                } catch (_: SerializationException) {
                    emptyList()
                } catch (_: IllegalArgumentException) {
                    emptyList()
                }
            } ?: emptyList()

            val updated = current.toMutableList()
            val existingIndex = updated.indexOfFirst { it.id == order.id }
            if (existingIndex >= 0) {
                updated[existingIndex] = order
            } else {
                updated.add(order)
            }

            val sorted = updated.sortedByDescending { it.createdAtEpochMillis ?: Long.MIN_VALUE }
            prefs[ordersKey] = json.encodeToString(sorted)
            prefs[lastSyncKey] = lastSync
        }
    }

    suspend fun read(): List<LocalOrder> {
        return orders.first()
    }
}
