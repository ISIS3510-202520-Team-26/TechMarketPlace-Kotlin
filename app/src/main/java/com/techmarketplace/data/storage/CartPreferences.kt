package com.techmarketplace.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DEFAULT_TTL_MILLIS = 30L * 60L * 1000L

data class CartMetadata(
    val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    val lastSyncEpochMillis: Long? = null
)

private val Context.cartMetadataStore: DataStore<Preferences> by preferencesDataStore(name = "cart_metadata")

class CartPreferences internal constructor(
    private val dataStore: DataStore<Preferences>
) {

    constructor(context: Context) : this(context.applicationContext.cartMetadataStore)

    private val ttlKey = longPreferencesKey("ttl_millis")
    private val lastSyncKey = longPreferencesKey("last_sync")

    val metadata: Flow<CartMetadata> = dataStore.data.map { prefs ->
        val ttl = prefs[ttlKey] ?: DEFAULT_TTL_MILLIS
        val lastSync = prefs[lastSyncKey]
        CartMetadata(ttlMillis = ttl, lastSyncEpochMillis = lastSync)
    }

    suspend fun updateTtl(ttlMillis: Long) {
        dataStore.edit { prefs ->
            prefs[ttlKey] = ttlMillis
        }
    }

    suspend fun updateLastSync(epochMillis: Long) {
        dataStore.edit { prefs ->
            prefs[lastSyncKey] = epochMillis
        }
    }

    suspend fun clearLastSync() {
        dataStore.edit { prefs ->
            prefs.remove(lastSyncKey)
        }
    }
}
