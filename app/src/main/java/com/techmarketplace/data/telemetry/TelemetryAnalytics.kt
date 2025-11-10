package com.techmarketplace.data.telemetry

import com.techmarketplace.analytics.ListingTelemetryEvent
import com.techmarketplace.analytics.SearchTelemetryEvent
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

object TelemetryAnalytics {
    /**
     * Computes frequency counts for each filter identifier present in the provided events.
     */
    fun filterFrequency(events: Iterable<SearchTelemetryEvent.FilterApplied>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        events.forEach { event ->
            event.filterKeys
                .filter { it.isNotBlank() }
                .forEach { key -> counts[key] = (counts[key] ?: 0) + 1 }
        }
        return counts.toSortedMap()
    }

    /**
     * Groups listing creation events by day (UTC by default) and category identifier.
     */
    fun listingCreatedDailyCounts(
        events: Iterable<ListingTelemetryEvent.ListingCreated>,
        zoneId: ZoneId = ZoneOffset.UTC,
    ): Map<LocalDate, Map<String, Int>> {
        if (!events.iterator().hasNext()) return emptyMap()

        val dailyBuckets = sortedMapOf<LocalDate, MutableMap<String, Int>>()
        events.forEach { event ->
            val day = event.createdAt.atZone(zoneId).toLocalDate()
            val categoryKey = event.categoryId.takeUnless { it.isBlank() } ?: "unknown"
            val categoryCounts = dailyBuckets.getOrPut(day) { sortedMapOf() }
            categoryCounts[categoryKey] = (categoryCounts[categoryKey] ?: 0) + 1
        }

        return dailyBuckets.mapValues { (_, counts) -> counts.toMap() }
    }
}
