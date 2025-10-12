package com.techmarketplace.repo

import android.app.Application
import com.techmarketplace.net.ApiClient
import com.techmarketplace.net.api.ListingApi
import com.techmarketplace.net.dto.CatalogItemDto
import com.techmarketplace.net.dto.CreateListingRequest
import com.techmarketplace.net.dto.ListingOutDto
import com.techmarketplace.net.dto.PageListingOutDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class ListingsRepository(app: Application) {

    private val api: ListingApi = ApiClient.listingApi()

    // --- Catálogos ---
    suspend fun getCategories(): Result<List<CatalogItemDto>> = safeCall { api.getCategories() }

    suspend fun getBrands(categoryId: String? = null): Result<List<CatalogItemDto>> =
        safeCall { api.getBrands(categoryId) }

    // --- Listar ---
    suspend fun listListings(
        q: String? = null,
        categoryId: String? = null,
        brandId: String? = null,
        page: Int? = null,
        pageSize: Int? = null
    ): Result<PageListingOutDto> = safeCall {
        api.listListings(
            q = q,
            categoryId = categoryId,
            brandId = brandId,
            page = page,
            pageSize = pageSize
        )
    }

    // --- Crear ---
    suspend fun createListing(body: CreateListingRequest): Result<ListingOutDto> =
        safeCall { api.createListing(body) }

    // --- Helper estándar ---
    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: HttpException) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
