package com.techmarketplace.data.storage.dao

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object TelemetryTypeConverters {
    private val json = Json { ignoreUnknownKeys = true }
    private val rankingSerializer = ListSerializer(SellerRankingEntryEntity.serializer())
    private val demandCategorySerializer = ListSerializer(DemandCategoryStatEntity.serializer())
    private val demandButtonSerializer = ListSerializer(DemandButtonStatEntity.serializer())
    private val stringListSerializer = ListSerializer(String.serializer())

    @TypeConverter
    fun toRanking(jsonString: String?): List<SellerRankingEntryEntity> =
        jsonString?.let { json.decodeFromString(rankingSerializer, it) } ?: emptyList()

    @TypeConverter
    fun fromRanking(entries: List<SellerRankingEntryEntity>): String =
        json.encodeToString(rankingSerializer, entries)

    @TypeConverter
    fun toDemandCategory(jsonString: String?): List<DemandCategoryStatEntity> =
        jsonString?.let { json.decodeFromString(demandCategorySerializer, it) } ?: emptyList()

    @TypeConverter
    fun fromDemandCategory(entries: List<DemandCategoryStatEntity>): String =
        json.encodeToString(demandCategorySerializer, entries)

    @TypeConverter
    fun toDemandButton(jsonString: String?): List<DemandButtonStatEntity> =
        jsonString?.let { json.decodeFromString(demandButtonSerializer, it) } ?: emptyList()

    @TypeConverter
    fun fromDemandButton(entries: List<DemandButtonStatEntity>): String =
        json.encodeToString(demandButtonSerializer, entries)

    @TypeConverter
    fun toStringList(jsonString: String?): List<String> =
        jsonString?.let { json.decodeFromString(stringListSerializer, it) } ?: emptyList()

    @TypeConverter
    fun fromStringList(values: List<String>): String =
        json.encodeToString(stringListSerializer, values)
}
