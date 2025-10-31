// app/src/main/java/com/techmarketplace/storage/TokenStore.kt
package com.techmarketplace.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

// Single DataStore instance bound to Context (recommended pattern).
private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "auth_tokens"
)

class TokenStore(context: Context) {

    private val appCtx = context.applicationContext

    // Preference keys
    private val KEY_ACCESS = stringPreferencesKey("access_token")
    private val KEY_REFRESH = stringPreferencesKey("refresh_token")

    // Flows you can observe from ViewModels or interceptors
    val accessToken: Flow<String?> = appCtx.authDataStore.data.map { it[KEY_ACCESS] }
    val refreshToken: Flow<String?> = appCtx.authDataStore.data.map { it[KEY_REFRESH] }
    val hasTokens: Flow<Boolean> = appCtx.authDataStore.data.map { !it[KEY_ACCESS].isNullOrBlank() }

    data class Tokens(val accessToken: String?, val refreshToken: String?)
    val tokens: Flow<Tokens> = appCtx.authDataStore.data.map {
        Tokens(it[KEY_ACCESS], it[KEY_REFRESH])
    }

    // Persist tokens (pass null for refresh if backend doesn’t return it)
    suspend fun saveTokens(access: String, refresh: String?) {
        appCtx.authDataStore.edit { prefs ->
            prefs[KEY_ACCESS] = access
            if (refresh != null) prefs[KEY_REFRESH] = refresh
        }
    }

    // Clear everything (logout)
    suspend fun clear() {
        appCtx.authDataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS)
            prefs.remove(KEY_REFRESH)
        }
    }

    // One-shot getters (useful inside interceptors or init)
    suspend fun getAccessTokenOnce(): String? = accessToken.firstOrNull()
    suspend fun getRefreshTokenOnce(): String? = refreshToken.firstOrNull()

    // Synchronous peek (evita usar SharedPreferences diferentes)
    fun peekAccessToken(): String? = runBlocking { accessToken.firstOrNull() }
}
