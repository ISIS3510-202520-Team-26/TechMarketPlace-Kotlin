package com.techmarketplace.data.telemetry

import com.techmarketplace.analytics.ListingTelemetryEvent
import com.techmarketplace.analytics.SearchTelemetryEvent
import java.time.Instant
import java.time.LocalDate
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

    @Test
    fun listingCreatedDailyCounts_groups_by_day_and_category() {
        val events = listOf(
            ListingTelemetryEvent.ListingCreated(
                listingId = "listing-1",
                categoryId = "phones",
                createdAt = Instant.parse("2024-03-01T10:15:30Z")
            ),
            ListingTelemetryEvent.ListingCreated(
                listingId = "listing-2",
                categoryId = "phones",
                createdAt = Instant.parse("2024-03-01T23:59:59Z")
            ),
            ListingTelemetryEvent.ListingCreated(
                listingId = "listing-3",
                categoryId = "laptops",
                createdAt = Instant.parse("2024-03-02T00:00:01Z")
            ),
            ListingTelemetryEvent.ListingCreated(
                listingId = "listing-4",
                categoryId = "",
                createdAt = Instant.parse("2024-03-02T12:00:00Z")
            )
        )

        val counts = TelemetryAnalytics.listingCreatedDailyCounts(events)

        assertEquals(2, counts[LocalDate.parse("2024-03-01")]?.get("phones"))
        assertEquals(1, counts[LocalDate.parse("2024-03-02")]?.get("laptops"))
        assertEquals(1, counts[LocalDate.parse("2024-03-02")]?.get("unknown"))
    }
}
