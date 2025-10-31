// com/techmarketplace/telemetry/LoginTelemetry.kt
package com.techmarketplace.telemetry

import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.techmarketplace.BuildConfig
import com.techmarketplace.net.api.TelemetryApi
import com.techmarketplace.net.api.TelemetryBatch
import com.techmarketplace.net.api.TelemetryEvent
import kotlinx.coroutines.*
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object LoginTelemetry {
    private const val TAG = "LoginTelemetry"

    private var sessionId: String = UUID.randomUUID().toString()
    private var tokenProvider: (suspend () -> String?) = { null }         // debe devolver el access token
    private var networkProvider: (suspend () -> String?) = { null }       // "wifi" | "cell" | "none" | null
    private lateinit var api: TelemetryApi

    // scope propio (no se cancela en navegaciones)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // cold start
    @Volatile private var appStartElapsedMs: Long = 0L
    private val loginReported = AtomicBoolean(false)

    // -------- Utils --------
    private fun String.ensureSlash(): String = if (endsWith("/")) this else "$this/"
    private fun toStr(v: Any?): String? = when (v) {
        null -> null
        is String -> v
        is Number, is Boolean -> v.toString()
        else -> v.toString()
    }

    private fun buildRetrofit(baseUrl: String): Retrofit {
        val log = okhttp3.logging.HttpLoggingInterceptor().apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(log)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        val moshi: Moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl.ensureSlash())
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    // -------- Init / Session --------
    fun init(
        baseUrl: String = BuildConfig.API_BASE_URL,          // Debe terminar en /v1/
        tokenProvider: (suspend () -> String?) = { null },
        networkProvider: (suspend () -> String?) = { null }
    ) {
        this.tokenProvider = tokenProvider
        this.networkProvider = networkProvider
        api = buildRetrofit(baseUrl).create(TelemetryApi::class.java)
    }

    /** Llama en Application.onCreate() con SystemClock.elapsedRealtime() */
    fun setAppStart(elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()) {
        appStartElapsedMs = elapsedRealtimeMs
    }

    /** Nueva sesión (resetea anti-duplicado) */
    fun newSession() {
        sessionId = UUID.randomUUID().toString()
        loginReported.set(false)
    }

    // -------- Auth header helpers --------
    private suspend fun authHeaderOrNull(): String? {
        val t = tokenProvider()?.trim()
        return t?.let { if (it.startsWith("Bearer")) it else "Bearer $it" }
    }

    /** Espera hasta ~1.5s a que el token esté listo (DataStore) para evitar carreras post-login. */
    private suspend fun authHeaderWithRetry(): String? {
        repeat(6) { idx ->
            val h = authHeaderOrNull()
            if (h != null) return h
            delay(250)
            if (idx == 0) Log.d(TAG, "Esperando token para telemetry…")
        }
        return null
    }

    // -------- Eventos --------
    /** Login (solo una vez por sesión). */
    fun fireLoginSuccess(userIdOrEmail: String?) {
        if (!loginReported.compareAndSet(false, true)) return
        scope.launch {
            val coldStartMs = if (appStartElapsedMs > 0) {
                (SystemClock.elapsedRealtime() - appStartElapsedMs).coerceAtLeast(0L)
            } else null

            val net = runCatching { networkProvider() }.getOrNull()
            val device = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val os = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
            val locale = runCatching { Locale.getDefault().toLanguageTag() }.getOrNull()

            val props: Map<String, String?> = mapOf(
                "method" to "password",
                "app_version" to toStr(BuildConfig.VERSION_NAME),
                "app_code" to toStr(BuildConfig.VERSION_CODE),
                "device" to device,
                "os" to os,
                "locale" to locale,
                "net" to net,
                "cold_start_ms" to toStr(coldStartMs)
            )

            val ev = TelemetryEvent(
                event_type = "auth.login.success",
                session_id = sessionId,
                user_id = userIdOrEmail,
                occurred_at = Instant.now().toString(),
                properties = props
            )

            runCatching {
                val bearer = authHeaderWithRetry()
                if (bearer == null) {
                    Log.w(TAG, "No bearer (login). Evento omitido.")
                } else {
                    api.ingest(bearer = bearer, body = TelemetryBatch(listOf(ev)))
                }
            }.onFailure { Log.w(TAG, "ingest failed: ${it.message}") }
        }
    }

    /** Click en chip de categoría. */
    fun fireCategoryClick(categoryId: String, categoryName: String) {
        scope.launch {
            val props: Map<String, String?> = mapOf(
                "category_id" to (if (categoryId.isBlank()) "all" else categoryId),
                "category_name" to categoryName
            )

            val ev = TelemetryEvent(
                event_type = "category.clicked",
                session_id = sessionId,
                user_id = null,
                occurred_at = Instant.now().toString(),
                properties = props
            )

            runCatching {
                val bearer = authHeaderWithRetry()
                if (bearer == null) {
                    Log.w(TAG, "No bearer (category). Evento omitido.")
                } else {
                    api.ingest(bearer = bearer, body = TelemetryBatch(listOf(ev)))
                }
            }.onFailure { Log.w(TAG, "ingest failed: ${it.message}") }
        }
    }

    /** Wrapper no-suspend para usar directo desde onClick/onTap. */
    fun fireListingViewed(
        listingId: String,
        title: String?,
        categoryId: String?,
        categoryName: String?,
        brandId: String?,
        brandName: String?,
        priceCents: Int?,
        currency: String?
    ) {
        scope.launch {
            sendListingViewed(listingId, title, categoryId, categoryName, brandId, brandName, priceCents, currency)
        }
    }

    /** Versión suspend, por si ya estás en un scope. */
    suspend fun listingViewed(
        listingId: String,
        title: String?,
        categoryId: String?,
        categoryName: String?,
        brandId: String?,
        brandName: String?,
        priceCents: Int?,
        currency: String?
    ) = sendListingViewed(listingId, title, categoryId, categoryName, brandId, brandName, priceCents, currency)

    // Implementación común
    private suspend fun sendListingViewed(
        listingId: String,
        title: String?,
        categoryId: String?,
        categoryName: String?,
        brandId: String?,
        brandName: String?,
        priceCents: Int?,
        currency: String?
    ) {
        val props: Map<String, String?> = mapOf(
            "title" to toStr(title),
            "category_id" to toStr(categoryId),
            "category_name" to toStr(categoryName),
            "brand_id" to toStr(brandId),
            "brand_name" to toStr(brandName),
            "price_cents" to toStr(priceCents),
            "currency" to toStr(currency)
        )

        val ev = TelemetryEvent(
            event_type = "listing.viewed",
            session_id = sessionId,
            user_id = null,
            listing_id = listingId,
            occurred_at = Instant.now().toString(),
            properties = props
        )

        runCatching {
            val bearer = authHeaderWithRetry()
            if (bearer == null) {
                Log.w(TAG, "No bearer (listing.viewed). Evento omitido.")
            } else {
                api.ingest(bearer, TelemetryBatch(listOf(ev)))
            }
        }.onFailure { Log.w(TAG, "ingest failed: ${it.message}") }
    }

    // ---------- NUEVO: listing.created ----------
    /** Wrapper no-suspend para lanzar el evento al crear un listing. */
    fun fireListingCreated(
        listingId: String,
        title: String,
        categoryId: String?,
        categoryName: String?,
        brandId: String?,
        brandName: String?,
        priceCents: Int,
        currency: String?,
        quantity: Int? = null,
        condition: String? = null
    ) {
        scope.launch {
            sendListingCreated(
                listingId = listingId,
                title = title,
                categoryId = categoryId,
                categoryName = categoryName,
                brandId = brandId,
                brandName = brandName,
                priceCents = priceCents,
                currency = currency,
                quantity = quantity,
                condition = condition
            )
        }
    }

    /** Versión suspend del evento listing.created. */
    suspend fun listingCreated(
        listingId: String,
        title: String,
        categoryId: String?,
        categoryName: String?,
        brandId: String?,
        brandName: String?,
        priceCents: Int,
        currency: String?,
        quantity: Int? = null,
        condition: String? = null
    ) = sendListingCreated(
        listingId = listingId,
        title = title,
        categoryId = categoryId,
        categoryName = categoryName,
        brandId = brandId,
        brandName = brandName,
        priceCents = priceCents,
        currency = currency,
        quantity = quantity,
        condition = condition
    )

    /** Implementación común para listing.created */
    private suspend fun sendListingCreated(
        listingId: String,
        title: String,
        categoryId: String?,
        categoryName: String?,
        brandId: String?,
        brandName: String?,
        priceCents: Int,
        currency: String?,
        quantity: Int? = null,
        condition: String? = null
    ) {
        val props: Map<String, String?> = mapOf(
            "title" to toStr(title),
            "category_id" to toStr(categoryId),
            "category_name" to toStr(categoryName),
            "brand_id" to toStr(brandId),
            "brand_name" to toStr(brandName),
            "price_cents" to toStr(priceCents),
            "currency" to toStr(currency),
            "quantity" to toStr(quantity),
            "condition" to toStr(condition)
        )

        val ev = TelemetryEvent(
            event_type = "listing.created",
            session_id = sessionId,
            user_id = null,
            listing_id = listingId,
            occurred_at = Instant.now().toString(),
            properties = props
        )

        runCatching {
            val bearer = authHeaderWithRetry()
            if (bearer == null) {
                Log.w(TAG, "No bearer (listing.created). Evento omitido.")
            } else {
                api.ingest(bearer = bearer, body = TelemetryBatch(listOf(ev)))
            }
        }.onFailure { Log.w(TAG, "ingest failed: ${it.message}") }
    }
}
