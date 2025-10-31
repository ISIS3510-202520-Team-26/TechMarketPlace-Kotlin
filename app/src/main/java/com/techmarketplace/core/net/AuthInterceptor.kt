package com.techmarketplace.core.net

import com.techmarketplace.storage.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val path = req.url.encodedPath

        // No metas bearer en estas rutas o si ya viene:
        val skip = path.endsWith("/auth/login") ||
                path.endsWith("/auth/register") ||
                path.endsWith("/auth/refresh") ||
                req.header("Authorization") != null

        if (skip) return chain.proceed(req)

        // Lee token de forma síncrona (desde SharedPreferences)
        val token = tokenStore.peekAccessToken()
        return if (token.isNullOrBlank()) {
            chain.proceed(req)
        } else {
            val newReq = req.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
            chain.proceed(newReq)
        }
    }
}
