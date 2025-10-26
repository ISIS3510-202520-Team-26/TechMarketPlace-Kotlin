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

    // Config de kotlinx-serialization (coincide con tus @Serializable)
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    // Factories de APIs (todas usan el mismo Retrofit con kotlinx-serialization)
    fun listingApi(): ListingApi = retrofit.create()
    fun imagesApi(): ImagesApi = retrofit.create(ImagesApi::class.java)
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

        // Interceptor que agrega Accept y Authorization (cuando aplique)
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

        // Authenticator para refrescar tokens en 401 (evita bucles y no refresca en /auth/*)
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

                        // Retrofit "plano" y corto sólo para el refresh
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

        // Cliente "principal" para TODAS las APIs (incluida ImagesApi para presign/confirm)
        // NOTA: El PUT a MinIO NO usa este cliente; lo hace ImagesRepository con un OkHttp plano.
        okHttp = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)     // un poco más generoso que antes
            .readTimeout(2, TimeUnit.MINUTES)         // confirma/presign pueden tardar más si el server está ocupado
            .writeTimeout(2, TimeUnit.MINUTES)
            .addInterceptor(authHeaderInterceptor)
            .addInterceptor(logging)
            .authenticator(refreshAuthenticator)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL) // ej: http://10.0.2.2:8000/  (con /v1 si tu base lo incluye)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .client(okHttp)
            .build()
    }
}
