package com.techmarketplace.data.storage.dao

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object TelemetryTypeConverters {
    private val json = Json { ignoreUnknownKeys = true }
    private val rankingSerializer = ListSerializer(SellerRankingEntryEntity.serializer())

    @TypeConverter
    fun toRanking(jsonString: String?): List<SellerRankingEntryEntity> =
        jsonString?.let { json.decodeFromString(rankingSerializer, it) } ?: emptyList()

    @TypeConverter
    fun fromRanking(entries: List<SellerRankingEntryEntity>): String =
        json.encodeToString(rankingSerializer, entries)
}
