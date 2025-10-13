package com.techmarketplace.repo

import android.app.Application
import com.techmarketplace.net.ApiClient
import com.techmarketplace.net.api.ListingApi
import com.techmarketplace.net.dto.CatalogItemDto
import com.techmarketplace.net.dto.CreateListingRequest
import com.techmarketplace.net.dto.ListingDetailDto
import com.techmarketplace.net.dto.SearchListingsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class ListingsRepository(private val app: Application) {

    private val api: ListingApi by lazy { ApiClient.listingApi() }

    // -------- Catálogos --------
    suspend fun getCategories(): Result<List<CatalogItemDto>> = safeCall {
        api.getCategories()
    }

    suspend fun getBrands(categoryId: String? = null): Result<List<CatalogItemDto>> = safeCall {
        api.getBrands(categoryId)
    }

    // -------- Listados / búsqueda --------
    suspend fun searchListings(
        q: String? = null,
        categoryId: String? = null,
        brandId: String? = null,
        minPrice: Int? = null,
        maxPrice: Int? = null,
        nearLat: Double? = null,
        nearLon: Double? = null,
        radiusKm: Double? = null,
        page: Int? = 1,
        pageSize: Int? = 20
    ): Result<SearchListingsResponse> = safeCall {
        api.searchListings(
            q = q,
            categoryId = categoryId,
            brandId = brandId,
            minPrice = minPrice,
            maxPrice = maxPrice,
            nearLat = nearLat,
            nearLon = nearLon,
            radiusKm = radiusKm,
            page = page,
            pageSize = pageSize
        )
    }

    // -------- Detalle --------
    suspend fun getListingDetail(id: String): Result<ListingDetailDto> = safeCall {
        api.getListingDetail(id)
    }

    // -------- Crear --------
    suspend fun createListing(body: CreateListingRequest): Result<ListingDetailDto> = safeCall {
        api.createListing(body)
    }

    // -------- Helper común --------
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
