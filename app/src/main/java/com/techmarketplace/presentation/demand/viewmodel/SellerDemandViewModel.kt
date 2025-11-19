package com.techmarketplace.presentation.demand.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techmarketplace.core.connectivity.ConnectivityObserver
import com.techmarketplace.core.data.Resource
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.repository.DemandAnalyticsRepository
import com.techmarketplace.data.repository.TelemetryRepositoryImpl
import com.techmarketplace.data.storage.cache.DemandSnapshotMemoryCache
import com.techmarketplace.data.storage.dao.TelemetryDatabaseProvider
import com.techmarketplace.domain.demand.ObserveSellerDemandUseCase
import com.techmarketplace.domain.demand.SellerDemandSnapshot
import com.techmarketplace.domain.telemetry.TelemetryRepository
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SellerDemandUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val snapshot: SellerDemandSnapshot? = null,
    val filterFrequencies: Map<String, Int> = emptyMap(),
    val errorMessage: String? = null,
    val isOffline: Boolean = false
)

class SellerDemandViewModel(
    application: Application,
    private val sellerId: String,
    private val useCase: ObserveSellerDemandUseCase,
    private val telemetryRepository: TelemetryRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SellerDemandUiState(isLoading = true))
    val uiState: StateFlow<SellerDemandUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var filtersJob: Job? = null

    init {
        observeDemand()
        observeFilters()
    }

    private fun observeDemand() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            useCase(sellerId).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.update { state ->
                            if (state.snapshot == null) state.copy(isLoading = true, errorMessage = null)
                            else state.copy(isRefreshing = true, errorMessage = null)
                        }
                    }
                    is Resource.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            snapshot = resource.data,
                            errorMessage = null,
                            isOffline = !resource.isFresh
                        )
                    }
                    is Resource.Error -> {
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = resource.throwable.message ?: "No se pudo cargar la demanda",
                                isOffline = resource.throwable is IOException
                            )
                        }
                    }
                }
            }
        }
    }

    private fun observeFilters() {
        filtersJob?.cancel()
        filtersJob = viewModelScope.launch {
            telemetryRepository.observeFilterFrequencies().collect { filters ->
                _uiState.update { it.copy(filterFrequencies = filters) }
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        viewModelScope.launch {
            val result = useCase.refresh(sellerId)
            result.exceptionOrNull()?.let { error ->
                _uiState.update { state ->
                    state.copy(
                        isRefreshing = false,
                        errorMessage = error.message ?: "No se pudo actualizar",
                        isOffline = error is IOException
                    )
                }
            } ?: run {
                _uiState.update { it.copy(isRefreshing = false, errorMessage = null) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
        filtersJob?.cancel()
    }

    companion object {
        fun factory(app: Application, sellerId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = TelemetryDatabaseProvider.get(app)
                    val repository = DemandAnalyticsRepository(
                        analyticsApi = ApiClient.analyticsApi(),
                        listingApi = ApiClient.listingApi(),
                        demandDao = db.sellerDemandDao(),
                        memoryCache = DemandSnapshotMemoryCache()
                    )
                    val connectivity = ConnectivityObserver.observe(app)
                    val useCase = ObserveSellerDemandUseCase(repository, connectivity)
                    val telemetryRepo = TelemetryRepositoryImpl.create(app)
                    return SellerDemandViewModel(app, sellerId, useCase, telemetryRepo) as T
                }
            }
    }
}
