package com.techmarketplace.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.remove
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.techmarketplace.data.remote.dto.ListingSummaryDto
import com.techmarketplace.data.remote.dto.SearchListingsResponse
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.homeFeedCacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "home_feed_cache"
)

private const val DEFAULT_CACHE_LIMIT = 20
private val DEFAULT_CACHE_TTL_MILLIS: Long = TimeUnit.MINUTES.toMillis(2)

class HomeFeedCacheStore @JvmOverloads constructor(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val cacheLimit: Int = DEFAULT_CACHE_LIMIT,
    private val ttlMillis: Long = DEFAULT_CACHE_TTL_MILLIS,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val dataStore = context.applicationContext.homeFeedCacheDataStore
    private val payloadKey = stringPreferencesKey("payload")

    val cache: Flow<HomeFeedCache?> = dataStore.data.map { prefs ->
        val payload = prefs[payloadKey] ?: return@map null
        runCatching { json.decodeFromString<HomeFeedCache>(payload) }.getOrNull()
    }

    suspend fun read(): HomeFeedCache? = cache.firstOrNull()

    suspend fun save(response: SearchListingsResponse) {
        val items = response.items.take(cacheLimit)
        if (items.isEmpty()) {
            clear()
            return
        }
        val payload = HomeFeedCache(
            items = items,
            total = response.total,
            pageSize = items.size,
            hasNext = response.hasNext,
            savedAtEpochMillis = clock()
        )
        dataStore.edit { prefs ->
            prefs[payloadKey] = json.encodeToString(payload)
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(payloadKey)
        }
    }

    fun isFresh(cache: HomeFeedCache, now: Long = clock()): Boolean {
        return now - cache.savedAtEpochMillis <= ttlMillis
    }
}

@Serializable
data class HomeFeedCache(
    val items: List<ListingSummaryDto>,
    val total: Int,
    val pageSize: Int,
    val hasNext: Boolean,
    val savedAtEpochMillis: Long
) {
    fun toResponse(): SearchListingsResponse = SearchListingsResponse(
        items = items,
        total = total,
        page = 1,
        pageSize = pageSize,
        hasNext = hasNext
    )
}
