package com.techmarketplace.presentation.profile.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.remote.dto.ListingSummaryDto
import com.techmarketplace.data.remote.dto.UserMe
import com.techmarketplace.data.repository.AuthRepository
import com.techmarketplace.data.repository.ListingsRepository
import com.techmarketplace.data.storage.LocationStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

data class ProfileUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,

    val user: UserMe? = null,

    val listings: List<ListingSummaryDto> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 20,
    val hasNext: Boolean = false
)

class ProfileViewModel(
    private val authRepo: AuthRepository,
    private val listingsRepo: ListingsRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(ProfileUiState(loading = true))
    val ui: StateFlow<ProfileUiState> = _ui.asStateFlow()

    init {
        refresh() // carga inicial
    }

    /** Recarga completa: usuario + página 1 de “mis publicaciones” */
    fun refresh() {
        viewModelScope.launch {
            // marca refresh (si ya hay contenido) o loading (si es primer render)
            val firstLoad = _ui.value.user == null && _ui.value.listings.isEmpty()
            _ui.value = _ui.value.copy(
                loading = firstLoad,
                refreshing = !firstLoad,
                error = null
            )

            // 1) /auth/me
            val userRes = authRepo.me()

            // 2) /listings?mine=true&page=1
            val listRes = listingsRepo.myListings(page = 1, pageSize = _ui.value.pageSize)

            val user = userRes.getOrNull()
            val page1 = listRes.getOrNull()

            val errorMsg =
                userRes.exceptionOrNull()?.toUserMessage()
                    ?: listRes.exceptionOrNull()?.toUserMessage()

            _ui.value = _ui.value.copy(
                loading = false,
                refreshing = false,
                error = errorMsg,
                user = user ?: _ui.value.user,
                listings = page1?.items ?: emptyList(),
                page = page1?.page ?: 1,
                hasNext = page1?.hasNext ?: false
            )
        }
    }

    /** Carga perezosa de más resultados si el backend indica has_next */
    fun loadNext() {
        val s = _ui.value
        if (s.loading || s.refreshing || !s.hasNext) return

        viewModelScope.launch {
            _ui.value = s.copy(loading = true, error = null)
            val nextPage = s.page + 1
            val res = listingsRepo.myListings(page = nextPage, pageSize = s.pageSize)

            res.onSuccess { page ->
                _ui.value = _ui.value.copy(
                    loading = false,
                    listings = _ui.value.listings + page.items,
                    page = page.page,
                    hasNext = page.hasNext
                )
            }.onFailure { e ->
                _ui.value = _ui.value.copy(
                    loading = false,
                    error = e.toUserMessage()
                )
            }
        }
    }

    /** Permite limpiar banner de error desde la UI */
    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }

    // ---- Factory estilo LoginViewModel ----
    companion object {
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val authRepository = AuthRepository(app)
                    val listingsRepository = ListingsRepository(
                        api = ApiClient.listingApi(),
                        locationStore = LocationStore(app)
                    )
                    return ProfileViewModel(authRepository, listingsRepository) as T
                }
            }
    }
}

/** Mapea excepciones a mensajes para el usuario (simple y útil) */
private fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> {
        val httpCode = try { this.code() } catch (_: Throwable) { -1 }
        when (httpCode) {
            401 -> "Sesión expirada. Inicia sesión de nuevo."
            403 -> "No tienes permisos para esta acción."
            404 -> "No se encontró la información."
            in 500..599 -> "El servidor tuvo un problema (HTTP $httpCode)."
            else -> if (httpCode > 0) "Error del servidor (HTTP $httpCode)." else "Error del servidor."
        }
    }
    is IOException -> "Sin conexión. Inténtalo de nuevo."
    else -> this.message ?: "Ocurrió un error inesperado."
}
