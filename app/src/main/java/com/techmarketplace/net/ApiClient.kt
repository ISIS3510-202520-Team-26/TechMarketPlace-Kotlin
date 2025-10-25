// app/src/main/java/com/techmarketplace/net/ApiClient.kt
package com.techmarketplace.net

import android.annotation.SuppressLint
import android.content.Context
import com.techmarketplace.BuildConfig
import com.techmarketplace.net.api.AuthApi
import com.techmarketplace.net.api.ImagesApi
import com.techmarketplace.net.api.ListingApi
import com.techmarketplace.net.api.OrdersApi
import com.techmarketplace.net.api.PaymentsApi
import com.techmarketplace.net.api.TelemetryApi
import com.techmarketplace.net.api.PriceSuggestionsApi
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
    fun authApi(): AuthApi = retrofit.create()
    fun telemetryApi(): TelemetryApi = retrofit.create()
    fun ordersApi(): OrdersApi = retrofit.create()
    fun paymentsApi(): PaymentsApi = retrofit.create()
    fun priceSuggestionsApi(): PriceSuggestionsApi = retrofit.create()
    fun imagesApi(): ImagesApi = retrofit.create()

    /** Call once from Application or Activity: ApiClient.init(applicationContext) */
    fun init(appContext: Context) {
        if (this::tokenStore.isInitialized) return
        tokenStore = TokenStore(appContext)

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }

        val authHeaderInterceptor = Interceptor { chain ->
            val original = chain.request()
            val path = original.url.encodedPath
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

        val refreshAuthenticator = object : Authenticator {
            override fun authenticate(route: Route?, response: Response): Request? {
                if (responseCount(response) >= 2) return null

                val path = response.request.url.encodedPath
                if (path.contains("/auth/")) return null

                val currentAccess = runBlocking { tokenStore.accessToken.firstOrNull() }
                val requestAuth = response.request.header("Authorization")
                if (currentAccess.isNullOrBlank() || requestAuth == "Bearer $currentAccess") {
                    val refreshed = try {
                        val refresh = runBlocking { tokenStore.refreshToken.firstOrNull() } ?: return null

                        val plainRetrofit = Retrofit.Builder()
                            .baseUrl(BuildConfig.API_BASE_URL)
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
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(authHeaderInterceptor)
            .addInterceptor(logging)
            .authenticator(refreshAuthenticator)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .client(okHttp)
            .build()
    }
}
