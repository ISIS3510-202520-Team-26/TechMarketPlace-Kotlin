package com.techmarketplace.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceCoachDao {
    @Query("SELECT * FROM price_coach_snapshot WHERE sellerId = :sellerId LIMIT 1")
    suspend fun get(sellerId: String): PriceCoachSnapshotEntity?

    @Query("SELECT * FROM price_coach_snapshot WHERE sellerId = :sellerId LIMIT 1")
    fun observe(sellerId: String): Flow<PriceCoachSnapshotEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PriceCoachSnapshotEntity)

    @Query("DELETE FROM price_coach_snapshot WHERE fetchedAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long)
}
