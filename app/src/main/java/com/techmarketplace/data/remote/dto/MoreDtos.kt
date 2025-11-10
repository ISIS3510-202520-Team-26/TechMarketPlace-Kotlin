package com.techmarketplace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- Price Suggestion ----------
@Serializable
data class PriceSuggestionOut(
    @SerialName("id") val id: String,
    @SerialName("listing_id") val listingId: String,
    @SerialName("suggested_price_cents") val suggestedPriceCents: Int,
    @SerialName("algorithm") val algorithm: String,
    @SerialName("created_at") val createdAt: String? = null
)

// ---------- Orders ----------
@Serializable
data class OrderCreateIn(
    @SerialName("listing_id") val listingId: String,
    @SerialName("quantity") val quantity: Int = 1,
    @SerialName("total_cents") val totalCents: Int? = null,
    @SerialName("currency") val currency: String? = null,
    @SerialName("buyer_id") val buyerId: String? = null
)

@Serializable
data class OrderOut(
    @SerialName("id") val id: String,
    @SerialName("buyer_id") val buyerId: String? = null,
    @SerialName("seller_id") val sellerId: String? = null,
    @SerialName("listing_id") val listingId: String,
    @SerialName("listing_title") val listingTitle: String? = null,
    @SerialName("listing_thumbnail_url") val listingThumbnailUrl: String? = null,
    @SerialName("quantity") val quantity: Int? = null,
    @SerialName("total_cents") val totalCents: Int,
    @SerialName("currency") val currency: String,
    @SerialName("status") val status: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

// ---------- Telemetry ----------
@Serializable
data class TelemetryEventIn(
    @SerialName("event_type") val eventType: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("listing_id") val listingId: String? = null,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("chat_id") val chatId: String? = null,
    @SerialName("step") val step: String? = null,
    @SerialName("properties") val properties: Map<String, @Serializable(with = AnyAsString::class) Any?> = emptyMap(),
    @SerialName("occurred_at") val occurredAt: String? = null
)

@Serializable
data class TelemetryBatchIn(
    @SerialName("events") val events: List<TelemetryEventIn>
)

// ---- helper para map simple (casteo básico a string)
object AnyAsString : kotlinx.serialization.KSerializer<Any?> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("AnyAsString", kotlinx.serialization.descriptors.PrimitiveKind.STRING)
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Any?) {
        encoder.encodeString(value?.toString() ?: "null")
    }
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Any? = decoder.decodeString()
}
