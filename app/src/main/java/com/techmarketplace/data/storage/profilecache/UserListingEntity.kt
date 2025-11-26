package com.techmarketplace.data.storage.profilecache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_listings")
data class UserListingEntity(
    @PrimaryKey val id: String,
    val sellerId: String,
    val title: String,
    val priceCents: Int,
    val currency: String?,
    val thumbnailKey: String?,
    val thumbnailUrl: String?,
    val updatedAt: Long
)
