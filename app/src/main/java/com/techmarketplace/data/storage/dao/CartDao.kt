package com.techmarketplace.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CartDao {

    @Query("SELECT * FROM cart_items ORDER BY lastModifiedEpochMillis DESC")
    fun observeAll(): Flow<List<CartItemEntity>>

    @Query("SELECT * FROM cart_items WHERE cartItemId = :id")
    suspend fun getById(id: String): CartItemEntity?

    @Query("SELECT * FROM cart_items WHERE expiresAtEpochMillis IS NULL OR expiresAtEpochMillis > :now")
    suspend fun getActive(now: Long): List<CartItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CartItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<CartItemEntity>)

    @Query("DELETE FROM cart_items WHERE cartItemId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cart_items WHERE expiresAtEpochMillis IS NOT NULL AND expiresAtEpochMillis <= :now")
    suspend fun deleteExpired(now: Long): Int

    @Query("UPDATE cart_items SET quantity = :quantity, lastModifiedEpochMillis = :lastModified WHERE cartItemId = :id")
    suspend fun updateQuantity(id: String, quantity: Int, lastModified: Long)

    @Query("UPDATE cart_items SET pendingOperation = :operation, pendingQuantity = :quantity, lastModifiedEpochMillis = :lastModified WHERE cartItemId = :id")
    suspend fun markPending(id: String, operation: String?, quantity: Int?, lastModified: Long)

    @Query("UPDATE cart_items SET pendingOperation = NULL, pendingQuantity = NULL, lastModifiedEpochMillis = :lastModified WHERE cartItemId IN (:ids)")
    suspend fun clearPending(ids: List<String>, lastModified: Long)

    @Query("SELECT * FROM cart_items WHERE pendingOperation IS NOT NULL ORDER BY lastModifiedEpochMillis ASC")
    suspend fun getPending(): List<CartItemEntity>

    @Transaction
    suspend fun replaceAll(items: List<CartItemEntity>) {
        deleteAll()
        upsert(items)
    }

    @Query("DELETE FROM cart_items")
    suspend fun deleteAll()
}
