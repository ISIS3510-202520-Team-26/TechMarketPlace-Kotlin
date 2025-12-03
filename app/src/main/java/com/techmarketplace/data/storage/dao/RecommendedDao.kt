package com.techmarketplace.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecommendedDao {

    @Query("SELECT * FROM recommended_items ORDER BY fetchedAt DESC")
    fun observe(): Flow<List<RecommendedItemsEntity>>

    @Query("SELECT * FROM recommended_items ORDER BY fetchedAt DESC")
    suspend fun getAll(): List<RecommendedItemsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RecommendedItemsEntity>)

    @Query("DELETE FROM recommended_items WHERE fetchedAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long)
}
