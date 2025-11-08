package com.techmarketplace.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SellerMetricsDao {
    @Query("SELECT * FROM seller_response_metrics WHERE sellerId = :sellerId LIMIT 1")
    fun observeMetrics(sellerId: String): Flow<SellerResponseMetricsEntity?>

    @Query("SELECT * FROM seller_response_metrics WHERE sellerId = :sellerId LIMIT 1")
    suspend fun getMetrics(sellerId: String): SellerResponseMetricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetrics(entity: SellerResponseMetricsEntity)

    @Query("DELETE FROM seller_response_metrics WHERE sellerId = :sellerId")
    suspend fun deleteBySellerId(sellerId: String)
}
