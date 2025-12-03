package com.techmarketplace.data.storage.dao

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_coach_snapshot")
data class PriceCoachSnapshotEntity(
    @PrimaryKey val sellerId: String,
    val fetchedAt: Long,
    val categoryId: String?,
    val brandId: String?,
    val suggestedPriceCents: Int?,
    val algorithm: String?,
    val gmvCents: Int?,
    val ordersPaid: Int?,
    val dau: Int?,
    val listingsInCategory: Int?,
    val windowDays: Int
)

data class PriceCoachSnapshot(
    val sellerId: String,
    val fetchedAt: Long,
    val categoryId: String?,
    val brandId: String?,
    val suggestedPriceCents: Int?,
    val algorithm: String?,
    val gmvCents: Int?,
    val ordersPaid: Int?,
    val dau: Int?,
    val listingsInCategory: Int?,
    val windowDays: Int,
    val source: String
)

fun PriceCoachSnapshotEntity.toDomain(source: String = "cache"): PriceCoachSnapshot =
    PriceCoachSnapshot(
        sellerId = sellerId,
        fetchedAt = fetchedAt,
        categoryId = categoryId,
        brandId = brandId,
        suggestedPriceCents = suggestedPriceCents,
        algorithm = algorithm,
        gmvCents = gmvCents,
        ordersPaid = ordersPaid,
        dau = dau,
        listingsInCategory = listingsInCategory,
        windowDays = windowDays,
        source = source
    )
