package com.techmarketplace.data.storage.dao

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.techmarketplace.domain.telemetry.SellerRankingEntry
import com.techmarketplace.domain.telemetry.SellerResponseMetrics
import kotlinx.serialization.Serializable

@Entity(tableName = "seller_response_metrics")
data class SellerResponseMetricsEntity(
    @PrimaryKey val sellerId: String,
    val responseRate: Double,
    val averageResponseMinutes: Double,
    val totalConversations: Int,
    val ranking: List<SellerRankingEntryEntity>,
    val fetchedAt: Long,
    val updatedAtIso: String?
)

@Serializable
data class SellerRankingEntryEntity(
    val sellerId: String,
    val sellerName: String?,
    val responseRate: Double,
    val averageResponseMinutes: Double,
    val percentile: Double?
)

fun SellerResponseMetricsEntity.toDomain(): SellerResponseMetrics =
    SellerResponseMetrics(
        sellerId = sellerId,
        responseRate = responseRate,
        averageResponseMinutes = averageResponseMinutes,
        totalConversations = totalConversations,
        ranking = ranking.map { entry ->
            SellerRankingEntry(
                sellerId = entry.sellerId,
                sellerName = entry.sellerName,
                responseRate = entry.responseRate,
                averageResponseMinutes = entry.averageResponseMinutes,
                percentile = entry.percentile
            )
        },
        fetchedAt = fetchedAt,
        updatedAtIso = updatedAtIso
    )
