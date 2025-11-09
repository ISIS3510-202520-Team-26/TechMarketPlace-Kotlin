package com.techmarketplace.data.repository

import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.remote.api.TelemetryApi
import com.techmarketplace.data.remote.dto.toEntity
import com.techmarketplace.data.storage.dao.SellerMetricsDao
import com.techmarketplace.data.storage.dao.TelemetryDatabaseProvider
import com.techmarketplace.data.storage.dao.toDomain
import com.techmarketplace.domain.telemetry.SellerResponseMetrics
import com.techmarketplace.domain.telemetry.TelemetryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TelemetryRepositoryImpl(
    private val api: TelemetryApi,
    private val dao: SellerMetricsDao,
    private val now: () -> Long = { System.currentTimeMillis() }
) : TelemetryRepository {

    override fun observeSellerResponseMetrics(sellerId: String): Flow<SellerResponseMetrics?> =
        dao.observeMetrics(sellerId).map { entity -> entity?.toDomain() }

    override suspend fun getCachedSellerResponseMetrics(sellerId: String): SellerResponseMetrics? =
        dao.getMetrics(sellerId)?.toDomain()

    override suspend fun refreshSellerResponseMetrics(sellerId: String) {
        val dto = api.getSellerResponseMetrics(sellerId)
        val entity = dto.toEntity(now())
        dao.insertMetrics(entity)
    }

    override suspend fun isCacheExpired(sellerId: String, ttlMillis: Long): Boolean {
        val entity = dao.getMetrics(sellerId) ?: return true
        return now() - entity.fetchedAt > ttlMillis
    }

    companion object {
        fun create(context: android.content.Context): TelemetryRepositoryImpl {
            val db = TelemetryDatabaseProvider.get(context)
            return TelemetryRepositoryImpl(ApiClient.telemetryApi(), db.sellerMetricsDao())
        }
    }
}
