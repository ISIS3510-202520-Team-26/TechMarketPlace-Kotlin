package com.techmarketplace.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore propio para los clics de categorías
private val Context.categoryClicksDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "category_clicks"
)

class CategoryClickStore(context: Context) {

    private val appCtx = context.applicationContext
    private val prefix = "cat_click_" // clave: cat_click_<id>

    private fun keyFor(id: String) = intPreferencesKey(prefix + id)

    /** Incrementa el contador local de clics para una categoría */
    suspend fun increment(categoryId: String) {
        if (categoryId.isBlank()) return // ignorar "All"
        appCtx.categoryClicksDataStore.edit { prefs ->
            val k = keyFor(categoryId)
            val cur = prefs[k] ?: 0
            prefs[k] = cur + 1
        }
    }

    /** Devuelve el conteo para una categoría específica */
    fun countFlow(categoryId: String): Flow<Int> =
        appCtx.categoryClicksDataStore.data.map { prefs ->
            if (categoryId.isBlank()) 0 else prefs[keyFor(categoryId)] ?: 0
        }

    /**
     * Lista de IDs de categorías ordenada por clics (desc).
     * Si no hay clics aún, lista vacía.
     */
    val topIdsFlow: Flow<List<String>> =
        appCtx.categoryClicksDataStore.data.map { prefs ->
            prefs.asMap()
                .mapNotNull { (k, v) ->
                    val name = k.name
                    if (name.startsWith(prefix)) {
                        val id = name.removePrefix(prefix)
                        val count = (v as? Int) ?: 0
                        id to count
                    } else null
                }
                .sortedByDescending { it.second }
                .map { it.first }
        }
}
