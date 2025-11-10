package com.techmarketplace.analytics

/**
 * Represents telemetry events originating from search and discovery flows.
 */
sealed interface SearchTelemetryEvent {
    val type: Type

    enum class Type {
        FilterApplied
    }

    /**
     * Event emitted whenever the user applies or changes search filters.
     *
     * @param filterKeys Stable identifiers describing the active filters (e.g. "category:phones").
     */
    data class FilterApplied(val filterKeys: Set<String>) : SearchTelemetryEvent {
        override val type: Type = Type.FilterApplied
    }
}
