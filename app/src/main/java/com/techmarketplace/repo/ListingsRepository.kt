package com.techmarketplace.repo

import android.app.Application
import com.techmarketplace.net.ApiClient
import com.techmarketplace.net.api.ListingApi
import com.techmarketplace.net.dto.CatalogItemDto
import com.techmarketplace.net.dto.CreateListingRequest
import com.techmarketplace.net.dto.ListingDetailDto
import com.techmarketplace.net.dto.SearchListingsRequest
import com.techmarketplace.net.dto.SearchListingsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class ListingsRepository(private val app: Application) {

    private val api: ListingApi by lazy { ApiClient.listingApi() }

    // --- Catalogs ---
    suspend fun getCategories(): Result<List<CatalogItemDto>> = safeCall {
        api.getCategories()
    }

    suspend fun getBrands(categoryId: String? = null): Result<List<CatalogItemDto>> = safeCall {
        api.getBrands(categoryId)
    }

    // --- Search / list ---
    suspend fun searchListings(req: SearchListingsRequest): Result<SearchListingsResponse> =
        safeCall {
            api.searchListings(
                q = req.q,
                categoryId = req.category_id,
                brandId = req.brand_id,
                minPrice = req.min_price,
                maxPrice = req.max_price,
                nearLat = req.near_lat,
                nearLon = req.near_lon,
                radiusKm = req.radius_km,
                page = req.page,
                pageSize = req.page_size
            )
        }

    // --- Detail ---
    suspend fun getListingDetail(id: String): Result<ListingDetailDto> = safeCall {
        api.getListingDetail(id)
    }

    // --- Create ---
    suspend fun createListing(body: CreateListingRequest): Result<ListingDetailDto> = safeCall {
        api.createListing(body)
    }

    // --- Helpers ---
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
