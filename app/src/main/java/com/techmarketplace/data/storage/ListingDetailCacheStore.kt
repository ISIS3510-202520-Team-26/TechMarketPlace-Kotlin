package com.techmarketplace.data.storage

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.techmarketplace.data.remote.dto.ListingDetailDto
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.listingDetailCacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "listing_detail_cache"
)

private const val DEFAULT_CACHE_LIMIT = 12
private val DEFAULT_CACHE_TTL_MILLIS: Long = TimeUnit.MINUTES.toMillis(10)

class ListingDetailCacheStore private constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    private val cacheLimit: Int,
    private val ttlMillis: Long,
    private val clock: () -> Long
) {

    @JvmOverloads
    constructor(
        context: Context,
        json: Json = Json { ignoreUnknownKeys = true },
        cacheLimit: Int = DEFAULT_CACHE_LIMIT,
        ttlMillis: Long = DEFAULT_CACHE_TTL_MILLIS,
        clock: () -> Long = { System.currentTimeMillis() }
    ) : this(
        dataStore = context.applicationContext.listingDetailCacheDataStore,
        json = json,
        cacheLimit = cacheLimit,
        ttlMillis = ttlMillis,
        clock = clock
    )

    companion object {
        fun fromDataStore(
            dataStore: DataStore<Preferences>,
            json: Json = Json { ignoreUnknownKeys = true },
            cacheLimit: Int = DEFAULT_CACHE_LIMIT,
            ttlMillis: Long = DEFAULT_CACHE_TTL_MILLIS,
            clock: () -> Long = { System.currentTimeMillis() }
        ): ListingDetailCacheStore = ListingDetailCacheStore(
            dataStore = dataStore,
            json = json,
            cacheLimit = cacheLimit,
            ttlMillis = ttlMillis,
            clock = clock
        )
    }

    private val payloadKey = stringPreferencesKey("payload")

    val entries: Flow<List<CachedListingDetail>> = dataStore.data.map { prefs ->
        val payload = prefs[payloadKey] ?: return@map emptyList()
        runCatching { json.decodeFromString<CachePayload>(payload) }
            .getOrNull()
            ?.entries
            ?.filter { isFresh(it) }
            ?: emptyList()
    }

    suspend fun get(id: String): CachedListingDetail? {
        var result: CachedListingDetail? = null
        dataStore.edit { prefs ->
            val payload = decode(prefs[payloadKey])
            val now = clock()
            val fresh = payload.entries.filter { now - it.savedAtEpochMillis <= ttlMillis }
            val match = fresh.firstOrNull { it.listingId == id }
            val reordered = if (match != null) {
                listOf(match) + fresh.filterNot { it.listingId == id }
            } else {
                fresh
            }
            val changed = fresh.size != payload.entries.size || match != null
            if (changed) {
                persist(prefs, reordered)
            }
            result = match
        }
        return result
    }

    suspend fun save(detail: ListingDetailDto): CachedListingDetail {
        val entry = CachedListingDetail(
            listingId = detail.id,
            detail = detail,
            savedAtEpochMillis = clock()
        )
        dataStore.edit { prefs ->
            val payload = decode(prefs[payloadKey])
            val survivors = payload.entries
                .filter { it.listingId != detail.id && isFresh(it, entry.savedAtEpochMillis) }
            val updated = listOf(entry) + survivors
            persist(prefs, updated)
        }
        return entry
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(payloadKey) }
    }

    fun isFresh(entry: CachedListingDetail, now: Long = clock()): Boolean =
        (now - entry.savedAtEpochMillis) <= ttlMillis

    @VisibleForTesting
    suspend fun snapshot(): List<CachedListingDetail> {
        val prefs = dataStore.data.firstOrNull() ?: return emptyList()
        val payload = decode(prefs[payloadKey])
        val now = clock()
        return payload.entries.filter { isFresh(it, now) }
    }

    private fun decode(raw: String?): CachePayload =
        raw?.let { runCatching { json.decodeFromString<CachePayload>(it) }.getOrNull() }
            ?: CachePayload(emptyList())

    private fun persist(prefs: MutablePreferences, entries: List<CachedListingDetail>) {
        prefs[payloadKey] = json.encodeToString(CachePayload(entries.take(cacheLimit)))
    }

    @Serializable
    private data class CachePayload(
        val entries: List<CachedListingDetail>
    )
}

@Serializable
data class CachedListingDetail(
    val listingId: String,
    val detail: ListingDetailDto,
    val savedAtEpochMillis: Long
)
