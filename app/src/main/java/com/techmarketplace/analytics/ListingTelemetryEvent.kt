package com.techmarketplace.analytics

import java.time.Instant

/**
 * Telemetry events originating from listing creation flows.
 */
sealed interface ListingTelemetryEvent {
    val type: Type

    enum class Type {
        ListingCreated,
    }

    /**
     * Emitted after a listing has been successfully published.
     *
     * @param listingId Identifier returned by the backend.
     * @param categoryId Category associated with the listing.
     * @param createdAt Timestamp in UTC describing when the listing was created.
     */
    data class ListingCreated(
        val listingId: String,
        val categoryId: String,
        val createdAt: Instant,
    ) : ListingTelemetryEvent {
        override val type: Type = Type.ListingCreated
    }
}
