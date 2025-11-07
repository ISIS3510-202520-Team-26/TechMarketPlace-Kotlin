// app/src/main/java/com/techmarketplace/net/ApiClient.kt
package com.techmarketplace.data.remote

import android.annotation.SuppressLint
import android.content.Context
import com.techmarketplace.BuildConfig
import com.techmarketplace.data.remote.api.AuthApi
import com.techmarketplace.data.remote.api.ImagesApi
import com.techmarketplace.data.remote.api.ListingApi
import com.techmarketplace.data.remote.api.OrdersApi
import com.techmarketplace.data.remote.api.PaymentsApi
import com.techmarketplace.data.remote.api.PriceSuggestionsApi
import com.techmarketplace.data.remote.api.TelemetryApi
import com.techmarketplace.data.remote.dto.RefreshRequest
import com.techmarketplace.data.storage.TokenStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import retrofit2.create

@SuppressLint("StaticFieldLeak")
object ApiClient {

    private lateinit var tokenStore: TokenStore
    private lateinit var okHttp: OkHttpClient
    private lateinit var retrofit: Retrofit

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun listingApi(): ListingApi = retrofit.create()
    fun imagesApi(): ImagesApi = retrofit.create()
    fun authApi(): AuthApi = retrofit.create()
    fun telemetryApi(): TelemetryApi = retrofit.create()
    fun ordersApi(): OrdersApi = retrofit.create()
    fun paymentsApi(): PaymentsApi = retrofit.create()
    fun priceSuggestionsApi(): PriceSuggestionsApi = retrofit.create()

    /** Llamar una vez desde Application o Activity: ApiClient.init(applicationContext) */
    fun init(appContext: Context) {
        if (this::tokenStore.isInitialized) return
        tokenStore = TokenStore(appContext)

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }

        // Helper: sólo se omite el bearer en login/register/refresh (NO en /auth/me)
        fun shouldSkipBearer(path: String): Boolean {
            // funciona con o sin /v1 al inicio
            return path.endsWith("auth/login") ||
                    path.endsWith("auth/register") ||
                    path.endsWith("auth/refresh")
        }

        // Interceptor que agrega Accept y Authorization (cuando aplique)
        val authHeaderInterceptor = Interceptor { chain ->
            val original = chain.request()
            val path = original.url.encodedPath

            val builder = original.newBuilder()
                .header("Accept", "application/json")

            val hasAuthHeader = original.header("Authorization") != null
            if (!shouldSkipBearer(path) && !hasAuthHeader) {
                val token = runBlocking { tokenStore.accessToken.firstOrNull() }
                if (!token.isNullOrBlank()) {
                    builder.header("Authorization", "Bearer $token")
                }
            }
            chain.proceed(builder.build())
        }

        // Authenticator que refresca en 401 (tampoco se activa para login/register/refresh)
        val refreshAuthenticator = object : Authenticator {
            override fun authenticate(route: Route?, response: Response): Request? {
                // evita bucles
                if (responseCount(response) >= 2) return null

                val path = response.request.url.encodedPath
                if (shouldSkipBearer(path)) return null  // permite refrescar en /auth/me

                val currentAccess = runBlocking { tokenStore.accessToken.firstOrNull() }
                val requestAuth = response.request.header("Authorization")
                // refresca sólo si el request fallido llevaba el access token actual
                if (currentAccess.isNullOrBlank() || requestAuth == "Bearer $currentAccess") {
                    val refreshed = try {
                        val refresh = runBlocking { tokenStore.refreshToken.firstOrNull() } ?: return null

                        // Retrofit "plano" para /auth/refresh
                        val plainRetrofit = Retrofit.Builder()
                            .baseUrl(ensureSlash(BuildConfig.API_BASE_URL))
                            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                            .client(
                                OkHttpClient.Builder()
                                    .connectTimeout(15, TimeUnit.SECONDS)
                                    .readTimeout(15, TimeUnit.SECONDS)
                                    .writeTimeout(15, TimeUnit.SECONDS)
                                    .build()
                            )
                            .build()

                        val api = plainRetrofit.create(AuthApi::class.java)
                        runBlocking { api.refresh(RefreshRequest(refresh)) }
                    } catch (_: Exception) {
                        return null
                    }

                    runBlocking {
                        tokenStore.saveTokens(
                            access = refreshed.access_token,
                            refresh = refreshed.refresh_token
                        )
                    }
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer ${refreshed.access_token}")
                        .build()
                }
                return null
            }

            private fun responseCount(response: Response): Int {
                var r: Response? = response
                var count = 1
                while (r?.priorResponse != null) {
                    count++
                    r = r.priorResponse
                }
                return count
            }
        }

        okHttp = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(2, TimeUnit.MINUTES)
            .addInterceptor(authHeaderInterceptor)
            .addInterceptor(logging)
            .authenticator(refreshAuthenticator)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(ensureSlash(BuildConfig.API_BASE_URL)) // ej: http://10.0.2.2:8000/ o http://10.0.2.2:8000/v1/
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .client(okHttp)
            .build()
    }

    private fun ensureSlash(base: String): String =
        if (base.endsWith("/")) base else "$base/"
}
