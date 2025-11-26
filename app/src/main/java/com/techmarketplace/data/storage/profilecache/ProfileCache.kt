package com.techmarketplace.data.storage.profilecache

import android.content.Context
import com.techmarketplace.data.remote.dto.ListingSummaryDto

class ProfileCache(context: Context) {
    private val db = ProfileCacheDatabase.create(context)
    private val dao = db.userListingDao()

    suspend fun savePage(sellerId: String, dtos: List<ListingSummaryDto>) {
        val now = System.currentTimeMillis()
        val entities = dtos.map { dto ->
            val p = dto.photos.firstOrNull()
            UserListingEntity(
                id = dto.id,
                sellerId = (dto.sellerId ?: sellerId),
                title = dto.title,
                priceCents = dto.priceCents,
                currency = dto.currency,
                thumbnailKey = p?.storageKey,
                thumbnailUrl = p?.imageUrl,
                updatedAt = now
            )
        }
        dao.upsertAll(entities)
    }

    suspend fun loadAll(sellerId: String): List<UserListingEntity> =
        dao.allForSeller(sellerId)

    suspend fun deleteById(id: String) = dao.deleteById(id)
}
