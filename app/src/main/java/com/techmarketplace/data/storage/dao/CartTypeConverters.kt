package com.techmarketplace.data.storage.dao

import androidx.room.TypeConverter
import com.techmarketplace.domain.cart.CartSyncOperation
import com.techmarketplace.domain.cart.CartVariantDetail
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
private const val AES_KEY_ALGORITHM = "AES"
private const val AES_KEY_BYTES = 16

object CartTypeConverters {

    private val json = Json { ignoreUnknownKeys = true }
    private val variantSerializer = ListSerializer(CartVariantDetail.serializer())

    private val secretKey by lazy {
        val digest = MessageDigest.getInstance("SHA-256").digest("techmarketplace-cart-key".toByteArray(StandardCharsets.UTF_8))
        SecretKeySpec(digest.copyOf(AES_KEY_BYTES), AES_KEY_ALGORITHM)
    }

    private val secureRandom = SecureRandom()

    @TypeConverter
    fun fromVariantDetails(details: List<CartVariantDetail>?): String {
        if (details.isNullOrEmpty()) return ""
        val plain = json.encodeToString(variantSerializer, details)
        return encrypt(plain)
    }

    @TypeConverter
    fun toVariantDetails(payload: String?): List<CartVariantDetail> {
        if (payload.isNullOrEmpty()) return emptyList()
        val decoded = runCatching { decrypt(payload) }.getOrElse { payload }
        return runCatching { json.decodeFromString(variantSerializer, decoded) }.getOrElse { emptyList() }
    }

    @TypeConverter
    fun fromPendingOperation(operation: CartSyncOperation?): String? = operation?.name

    @TypeConverter
    fun toPendingOperation(value: String?): CartSyncOperation? = value?.let { runCatching { CartSyncOperation.valueOf(it) }.getOrNull() }

    private fun encrypt(plain: String): String {
        return runCatching {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
            val ciphertext = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
            val payload = iv + ciphertext
            Base64.getEncoder().encodeToString(payload)
        }.getOrElse { plain }
    }

    private fun decrypt(encoded: String): String {
        return runCatching {
            val payload = Base64.getDecoder().decode(encoded)
            val iv = payload.copyOfRange(0, 12)
            val ciphertext = payload.copyOfRange(12, payload.size)
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val plainBytes = cipher.doFinal(ciphertext)
            String(plainBytes, StandardCharsets.UTF_8)
        }.getOrElse { encoded }
    }
}
