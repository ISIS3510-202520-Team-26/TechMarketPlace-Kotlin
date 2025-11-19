package com.techmarketplace.domain.demand

data class SellerDemandSnapshot(
    val sellerId: String,
    val fetchedAt: Long,
    val trendingCategories: List<DemandCategoryStat>,
    val quickViewCategories: List<DemandCategoryStat>,
    val buttonStats: List<ButtonClickStat>,
    val sellerCategoryIds: Set<String>
)

data class DemandCategoryStat(
    val categoryId: String,
    val categoryName: String,
    val totalCount: Int,
    /** 0.0–1.0 normalized share vs the most demanded category in the window. */
    val share: Double,
    val isSellerCategory: Boolean
)

data class ButtonClickStat(
    val button: String,
    val totalCount: Int
)
