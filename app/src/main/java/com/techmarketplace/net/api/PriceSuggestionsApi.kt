// app/src/main/java/com/techmarketplace/net/api/PriceSuggestionsApi.kt
package com.techmarketplace.net.api

import com.techmarketplace.net.dto.PriceSuggestionOut
import retrofit2.http.GET
import retrofit2.http.Query

interface PriceSuggestionsApi {
    @GET("price-suggestions/suggest")
    suspend fun getSuggestion(
        @Query("category_id") categoryId: String? = null,
        @Query("brand_id") brandId: String? = null
    ): PriceSuggestionOut
}
