package com.techmarketplace.data.storage.dao

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.techmarketplace.domain.demand.ButtonClickStat
import com.techmarketplace.domain.demand.DemandCategoryStat
import com.techmarketplace.domain.demand.SellerDemandSnapshot
import kotlinx.serialization.Serializable

@Entity(tableName = "seller_demand_snapshots")
data class SellerDemandSnapshotEntity(
    @PrimaryKey val sellerId: String,
    val fetchedAt: Long,
    val trendingCategories: List<DemandCategoryStatEntity>,
    val quickViewCategories: List<DemandCategoryStatEntity>,
    val buttonStats: List<DemandButtonStatEntity>,
    val sellerCategories: List<String>
)

@Serializable
data class DemandCategoryStatEntity(
    val categoryId: String,
    val categoryName: String,
    val totalCount: Int,
    val share: Double,
    val isSellerCategory: Boolean
)

@Serializable
data class DemandButtonStatEntity(
    val button: String,
    val totalCount: Int
)

fun SellerDemandSnapshotEntity.toDomain(): SellerDemandSnapshot =
    SellerDemandSnapshot(
        sellerId = sellerId,
        fetchedAt = fetchedAt,
        trendingCategories = trendingCategories.map { it.toDomain() },
        quickViewCategories = quickViewCategories.map { it.toDomain() },
        buttonStats = buttonStats.map { it.toDomain() },
        sellerCategoryIds = sellerCategories.toSet()
    )

fun DemandCategoryStatEntity.toDomain(): DemandCategoryStat =
    DemandCategoryStat(
        categoryId = categoryId,
        categoryName = categoryName,
        totalCount = totalCount,
        share = share,
        isSellerCategory = isSellerCategory
    )

fun DemandButtonStatEntity.toDomain(): ButtonClickStat =
    ButtonClickStat(
        button = button,
        totalCount = totalCount
    )
