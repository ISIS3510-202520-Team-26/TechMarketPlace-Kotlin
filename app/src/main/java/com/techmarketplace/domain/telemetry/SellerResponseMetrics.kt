package com.techmarketplace.domain.telemetry

data class SellerRankingEntry(
    val sellerId: String,
    val sellerName: String?,
    val responseRate: Double,
    val averageResponseMinutes: Double,
    val percentile: Double?
)

data class SellerResponseMetrics(
    val sellerId: String,
    val responseRate: Double,
    val averageResponseMinutes: Double,
    val totalConversations: Int,
    val ranking: List<SellerRankingEntry>,
    /** Epoch milliseconds when the snapshot was stored locally. */
    val fetchedAt: Long,
    /** Optional ISO date from backend for extra context. */
    val updatedAtIso: String?
)
