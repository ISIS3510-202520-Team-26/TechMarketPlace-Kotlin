package com.techmarketplace.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.locationDataStore by preferencesDataStore(name = "location_store")

class LocationStore(private val context: Context) {

    object Keys {
        val LAT = doublePreferencesKey("lat")
        val LON = doublePreferencesKey("lon")
        val TS  = longPreferencesKey("saved_at_epoch_ms")
    }

    data class StoredLocation(
        val lat: Double?,
        val lon: Double?,
        val savedAtEpochMs: Long?
    )

    val locationFlow: Flow<StoredLocation> = context.locationDataStore.data.map { prefs ->
        StoredLocation(
            lat = prefs[Keys.LAT],
            lon = prefs[Keys.LON],
            savedAtEpochMs = prefs[Keys.TS]
        )
    }

    suspend fun save(lat: Double, lon: Double, nowEpochMs: Long = System.currentTimeMillis()) {
        context.locationDataStore.edit { prefs ->
            prefs[Keys.LAT] = lat
            prefs[Keys.LON] = lon
            prefs[Keys.TS]  = nowEpochMs
        }
    }
}
