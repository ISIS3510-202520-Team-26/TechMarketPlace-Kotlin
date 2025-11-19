package com.techmarketplace.data.storage.cache

import android.util.LruCache
import com.techmarketplace.domain.demand.SellerDemandSnapshot

class DemandSnapshotMemoryCache(maxEntries: Int = 3) {

    private val cache = object : LruCache<String, SellerDemandSnapshot>(maxEntries) {
        override fun sizeOf(key: String, value: SellerDemandSnapshot): Int = 1
    }

    fun get(sellerId: String): SellerDemandSnapshot? =
        synchronized(cache) { cache.get(sellerId) }

    fun put(snapshot: SellerDemandSnapshot) {
        synchronized(cache) { cache.put(snapshot.sellerId, snapshot) }
    }

    fun remove(sellerId: String) {
        synchronized(cache) { cache.remove(sellerId) }
    }
}
