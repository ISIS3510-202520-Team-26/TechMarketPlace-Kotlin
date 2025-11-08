package com.techmarketplace.data.remote.dto

import com.techmarketplace.data.storage.dao.SellerRankingEntryEntity
import com.techmarketplace.data.storage.dao.SellerResponseMetricsEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SellerRankingEntryDto(
    @SerialName("seller_id") val sellerId: String,
    @SerialName("seller_name") val sellerName: String? = null,
    @SerialName("response_rate") val responseRate: Double? = null,
    @SerialName("average_response_minutes") val averageResponseMinutes: Double? = null,
    @SerialName("percentile") val percentile: Double? = null
)

@Serializable
data class SellerResponseMetricsDto(
    @SerialName("seller_id") val sellerId: String,
    @SerialName("response_rate") val responseRate: Double,
    @SerialName("average_response_minutes") val averageResponseMinutes: Double,
    @SerialName("total_conversations") val totalConversations: Int,
    @SerialName("ranking") val ranking: List<SellerRankingEntryDto> = emptyList(),
    @SerialName("updated_at") val updatedAt: String? = null
)

fun SellerResponseMetricsDto.toEntity(fetchedAt: Long): SellerResponseMetricsEntity =
    SellerResponseMetricsEntity(
        sellerId = sellerId,
        responseRate = responseRate,
        averageResponseMinutes = averageResponseMinutes,
        totalConversations = totalConversations,
        ranking = ranking.map { entry ->
            SellerRankingEntryEntity(
                sellerId = entry.sellerId,
                sellerName = entry.sellerName,
                responseRate = entry.responseRate ?: 0.0,
                averageResponseMinutes = entry.averageResponseMinutes ?: 0.0,
                percentile = entry.percentile
            )
        },
        fetchedAt = fetchedAt,
        updatedAtIso = updatedAt
    )
