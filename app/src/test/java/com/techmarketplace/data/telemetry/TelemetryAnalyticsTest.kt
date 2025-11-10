package com.techmarketplace.data.telemetry

import com.techmarketplace.analytics.SearchTelemetryEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryAnalyticsTest {

    @Test
    fun filterFrequency_counts_occurrences_by_key() {
        val events = listOf(
            SearchTelemetryEvent.FilterApplied(setOf("category:phones", "near:5km")),
            SearchTelemetryEvent.FilterApplied(setOf("category:phones")),
            SearchTelemetryEvent.FilterApplied(setOf("brand:acme"))
        )

        val counts = TelemetryAnalytics.filterFrequency(events)

        assertEquals(2, counts["category:phones"])
        assertEquals(1, counts["near:5km"])
        assertEquals(1, counts["brand:acme"])
    }

    @Test
    fun filterFrequency_ignores_blank_keys_and_empty_sets() {
        val events = listOf(
            SearchTelemetryEvent.FilterApplied(setOf("", "category:phones")),
            SearchTelemetryEvent.FilterApplied(emptySet())
        )

        val counts = TelemetryAnalytics.filterFrequency(events)

        assertEquals(mapOf("category:phones" to 1), counts)
    }
}
