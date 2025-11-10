package com.techmarketplace.data.telemetry

import com.techmarketplace.analytics.SearchTelemetryEvent

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
}
