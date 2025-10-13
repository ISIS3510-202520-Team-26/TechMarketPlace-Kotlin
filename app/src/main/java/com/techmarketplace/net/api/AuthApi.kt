package com.techmarketplace.net.api

import com.techmarketplace.net.dto.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): UserMe

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): TokenPair

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenPair

    @GET("auth/me")
    suspend fun me(): UserMe

    @POST("auth/google") // adjust if your backend uses a different path
    suspend fun loginWithGoogle(@Body body: GoogleLoginRequest): TokenPair
}
