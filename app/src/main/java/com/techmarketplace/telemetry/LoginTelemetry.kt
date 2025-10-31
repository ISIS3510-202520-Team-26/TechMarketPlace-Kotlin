// com/techmarketplace/telemetry/LoginTelemetry.kt
package com.techmarketplace.telemetry

import android.util.Log
import com.techmarketplace.BuildConfig
import com.techmarketplace.net.api.TelemetryApi
import com.techmarketplace.net.api.TelemetryBatch
import com.techmarketplace.net.api.TelemetryEvent
import kotlinx.coroutines.*
import java.time.Instant
import java.util.UUID
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object LoginTelemetry {
    private var sessionId: String = UUID.randomUUID().toString()
    private var tokenProvider: (suspend () -> String?) = { null }
    private lateinit var api: TelemetryApi

    // 🔹 Scope propio que NO se cancela al navegar
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun String.ensureSlash(): String = if (endsWith("/")) this else "$this/"

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

    fun init(
        baseUrl: String = BuildConfig.API_BASE_URL, // termina en /v1/
        tokenProvider: (suspend () -> String?) = { null }
    ) {
        this.tokenProvider = tokenProvider
        api = buildRetrofit(baseUrl).create(TelemetryApi::class.java)
    }

    fun newSession() { sessionId = UUID.randomUUID().toString() }

    // 🔹 No suspend: lanza en scope propio para que no se cancele con el VM
    fun fireLoginSuccess(userId: String?) {
        scope.launch {
            val ev = TelemetryEvent(
                event_type = "auth.login.success",
                session_id = sessionId,
                user_id = userId,
                occurred_at = Instant.now().toString(),
                properties = emptyMap()
            )
            runCatching {
                val bearer = tokenProvider()?.let { if (it.startsWith("Bearer")) it else "Bearer $it" }
                api.ingest(bearer = bearer, body = TelemetryBatch(listOf(ev)))
            }.onFailure { Log.w("LoginTelemetry", "ingest failed: ${it.message}") }
        }
    }
}
