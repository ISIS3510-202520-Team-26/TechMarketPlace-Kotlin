package com.techmarketplace.presentation.telemetry.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techmarketplace.core.connectivity.ConnectivityObserver
import com.techmarketplace.core.data.Resource
import com.techmarketplace.data.repository.TelemetryRepositoryImpl
import com.techmarketplace.domain.telemetry.ObserveSellerResponseMetricsUseCase
import com.techmarketplace.domain.telemetry.SellerRankingEntry
import com.techmarketplace.domain.telemetry.SellerResponseMetrics
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SellerRankingRowUi(
    val position: Int,
    val sellerName: String,
    val responseRatePercent: Int,
    val averageResponseMinutes: Double
)

data class SellerResponseMetricsUi(
    val responseRatePercent: Int,
    val averageResponseMinutes: Double,
    val totalConversations: Int,
    val lastUpdatedMillis: Long,
    val ranking: List<SellerRankingRowUi>,
    val updatedAtIso: String?
)

data class TelemetryUiState(
    val isLoading: Boolean = false,
    val metrics: SellerResponseMetricsUi? = null,
    val errorMessage: String? = null,
    val isOffline: Boolean = false
)

class TelemetryViewModel(
    application: Application,
    private val useCase: ObserveSellerResponseMetricsUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TelemetryUiState(isLoading = true))
    val uiState: StateFlow<TelemetryUiState> = _uiState.asStateFlow()

    private var currentSellerId: String? = null
    private var observeJob: Job? = null

    fun observeSeller(sellerId: String) {
        if (sellerId == currentSellerId && observeJob?.isActive == true) return
        currentSellerId = sellerId
        observeJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        observeJob = viewModelScope.launch {
            useCase(sellerId).collect { resource ->
                when (resource) {
                    is Resource.Loading -> _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    is Resource.Success -> _uiState.value = TelemetryUiState(
                        isLoading = false,
                        metrics = resource.data.toUi(),
                        errorMessage = null,
                        isOffline = !resource.isFresh
                    )
                    is Resource.Error -> {
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                metrics = resource.cachedData?.toUi() ?: state.metrics,
                                errorMessage = resource.throwable.message ?: "No se pudo obtener métricas",
                                isOffline = resource.throwable is IOException
                            )
                        }
                    }
                }
            }
        }
    }

    fun refresh() {
        val sellerId = currentSellerId ?: return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = useCase.refresh(sellerId)
            result.exceptionOrNull()?.let { error ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "No se pudo actualizar",
                        isOffline = error is IOException
                    )
                }
            }
        }
    }

    private fun SellerResponseMetrics.toUi(): SellerResponseMetricsUi =
        SellerResponseMetricsUi(
            responseRatePercent = (responseRate * 100).toInt().coerceIn(0, 100),
            averageResponseMinutes = averageResponseMinutes,
            totalConversations = totalConversations,
            lastUpdatedMillis = fetchedAt,
            ranking = ranking.mapIndexed { index, entry -> entry.toUi(index) },
            updatedAtIso = updatedAtIso
        )

    private fun SellerRankingEntry.toUi(position: Int): SellerRankingRowUi =
        SellerRankingRowUi(
            position = position + 1,
            sellerName = sellerName?.ifBlank { sellerId } ?: sellerId,
            responseRatePercent = (responseRate * 100).toInt().coerceIn(0, 100),
            averageResponseMinutes = averageResponseMinutes
        )

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = TelemetryRepositoryImpl.create(app)
                val connectivity = ConnectivityObserver.observe(app)
                val useCase = ObserveSellerResponseMetricsUseCase(
                    repository = repository,
                    connectivityFlow = connectivity,
                    ttlMillis = TimeUnit.MINUTES.toMillis(15)
                )
                return TelemetryViewModel(app, useCase) as T
            }
        }
    }
}
