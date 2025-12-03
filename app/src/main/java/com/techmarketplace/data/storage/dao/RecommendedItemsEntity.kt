package com.techmarketplace.data.storage.dao

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.techmarketplace.data.remote.dto.ListingSummaryDto
import kotlinx.serialization.Serializable

@Entity(tableName = "recommended_items")
data class RecommendedItemsEntity(
    @PrimaryKey val categoryId: String,
    val categoryName: String?,
    val fetchedAt: Long,
    val listings: List<ListingSummaryDto>,
    val source: String,
    val windowDays: Int
)

@Serializable
data class RecommendedListingSummary(
    val id: String,
    val categoryId: String?,
    val categoryName: String?,
    val brandId: String?,
    val brandName: String?,
    val title: String,
    val priceCents: Int,
    val currency: String?,
    val imageUrl: String?,
    val cacheKey: String?
)

data class RecommendedBucket(
    val categoryId: String,
    val categoryName: String?,
    val fetchedAt: Long,
    val listings: List<ListingSummaryDto>,
    val source: String,
    val windowDays: Int
)

fun RecommendedItemsEntity.toDomain(): RecommendedBucket =
    RecommendedBucket(
        categoryId = categoryId,
        categoryName = categoryName,
        fetchedAt = fetchedAt,
        listings = listings,
        source = source,
        windowDays = windowDays
    )
