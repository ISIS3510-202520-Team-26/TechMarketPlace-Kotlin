package com.techmarketplace.net

import android.annotation.SuppressLint
import android.content.Context
import com.techmarketplace.BuildConfig
import com.techmarketplace.net.api.AuthApi
import com.techmarketplace.net.api.ListingApi
import com.techmarketplace.net.dto.RefreshRequest
import com.techmarketplace.storage.TokenStore
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

import com.techmarketplace.net.api.OrdersApi
import com.techmarketplace.net.api.PaymentsApi
import com.techmarketplace.net.api.TelemetryApi
@SuppressLint("StaticFieldLeak") // TokenStore keeps application context internally
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
    fun authApi(): AuthApi = retrofit.create()

    /** Call once from Application or Activity: ApiClient.init(applicationContext) */
    fun init(appContext: Context) {
        if (this::tokenStore.isInitialized) return
        tokenStore = TokenStore(appContext)

        // Log body only for debug builds
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }

        // Adds Authorization header if we have an access token (but NOT for /auth/* endpoints)
        val authHeaderInterceptor = Interceptor { chain ->
            val original = chain.request()
            val path = original.url.encodedPath // e.g. /v1/auth/login
            val isAuthCall = path.contains("/auth/")

            val builder = original.newBuilder()
                .header("Accept", "application/json")

            if (!isAuthCall) {
                val token = runBlocking { tokenStore.accessToken.firstOrNull() }
                if (!token.isNullOrBlank()) {
                    builder.header("Authorization", "Bearer $token")
                }
            }
            chain.proceed(builder.build())
        }

        // On 401, try to refresh once and retry original request (but NOT for /auth/* endpoints)
        val refreshAuthenticator = object : Authenticator {
            override fun authenticate(route: Route?, response: Response): Request? {
                // Avoid loops (count prior responses)
                if (responseCount(response) >= 2) return null

                val path = response.request.url.encodedPath
                if (path.contains("/auth/")) return null // never refresh for /auth/*

                val currentAccess = runBlocking { tokenStore.accessToken.firstOrNull() }
                val requestAuth = response.request.header("Authorization")
                if (currentAccess.isNullOrBlank() || requestAuth == "Bearer $currentAccess") {
                    val refreshed = try {
                        val refresh = runBlocking { tokenStore.refreshToken.firstOrNull() } ?: return null

                        // Minimal Retrofit instance (no auth) just to call /auth/refresh
                        val plainRetrofit = Retrofit.Builder()
                            .baseUrl(BuildConfig.API_BASE_URL) // must end with /
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

                    // Save new pair and retry the original request with fresh token
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
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(authHeaderInterceptor)
            .addInterceptor(logging)
            .authenticator(refreshAuthenticator)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL) // e.g. http://10.0.2.2:8000/v1/ or http://<LAN_IP>:8000/v1/
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .client(okHttp)
            .build()
    }

    fun authApi(): AuthApi = retrofit.create(AuthApi::class.java)
    fun telemetryApi(): TelemetryApi = retrofit.create(TelemetryApi::class.java)
    fun ordersApi(): OrdersApi = retrofit.create(OrdersApi::class.java)
    fun paymentsApi(): PaymentsApi = retrofit.create(PaymentsApi::class.java)
}
