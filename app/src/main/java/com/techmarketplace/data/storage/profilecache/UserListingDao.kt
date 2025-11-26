package com.techmarketplace.data.storage.profilecache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserListingDao {

    @Query("""
        SELECT * FROM user_listings
        WHERE sellerId = :sellerId
        ORDER BY updatedAt DESC
    """)
    suspend fun allForSeller(sellerId: String): List<UserListingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<UserListingEntity>)

    @Query("DELETE FROM user_listings WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM user_listings WHERE sellerId = :sellerId")
    suspend fun deleteAllForSeller(sellerId: String)
}
