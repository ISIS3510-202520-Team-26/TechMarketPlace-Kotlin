package com.techmarketplace.data.local

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "location_store")

class LocationStore(private val context: Context) {

    private object Keys {
        val LAT = doublePreferencesKey("last_latitude")
        val LON = doublePreferencesKey("last_longitude")
    }

    /** Flujos de lectura (null si aún no hay valor) */
    val lastLatitudeFlow: Flow<Double?> =
        context.dataStore.data.map { it[Keys.LAT] }

    val lastLongitudeFlow: Flow<Double?> =
        context.dataStore.data.map { it[Keys.LON] }

    /** Escribe ambos valores de una sola vez */
    suspend fun saveLastLocation(latitude: Double, longitude: Double) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAT] = latitude
            prefs[Keys.LON] = longitude
        }
    }
}
