package com.techmarketplace.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SellerDemandDao {

    @Query("SELECT * FROM seller_demand_snapshots WHERE sellerId = :sellerId LIMIT 1")
    fun observeSnapshot(sellerId: String): Flow<SellerDemandSnapshotEntity?>

    @Query("SELECT * FROM seller_demand_snapshots WHERE sellerId = :sellerId LIMIT 1")
    suspend fun getSnapshot(sellerId: String): SellerDemandSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SellerDemandSnapshotEntity)

    @Query("DELETE FROM seller_demand_snapshots WHERE sellerId = :sellerId")
    suspend fun delete(sellerId: String)
}
