package com.techmarketplace.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.techmarketplace.data.remote.api.ListingApi
import com.techmarketplace.data.remote.dto.CatalogItemDto
import com.techmarketplace.data.remote.dto.CreateListingRequest
import com.techmarketplace.data.remote.dto.ListingDetailDto
import com.techmarketplace.data.remote.dto.SearchListingsResponse
import com.techmarketplace.data.storage.ListingDetailCacheStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingsRepositoryTest {

    @Test
    fun getListingDetail_fallsBackToCache_whenApiFails() = runTest {
        val cacheStore = createCacheStore(this)
        cacheStore.save(
            ListingDetailDto(
                id = "listing-1",
                title = "Cached",
                categoryId = "cat",
                priceCents = 10_000,
                currency = "COP",
                condition = "new",
                quantity = 1
            )
        )

        val api = FakeListingApi().apply { shouldFail = true }
        val repository = ListingsRepository(
            api = api,
            locationStore = null,
            homeFeedCacheStore = null,
            listingDetailCacheStore = cacheStore
        )

        val result = repository.getListingDetail("listing-1")

        assertEquals("listing-1", result.detail.id)
        assertEquals("Cached", result.detail.title)
        assertTrue(result.fromCache)
    }
}

private fun createCacheStore(scope: TestScope): ListingDetailCacheStore {
    val file = File.createTempFile("listing-detail-cache", ".preferences_pb").apply {
        deleteOnExit()
    }
    val dataStore = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { file }
    return ListingDetailCacheStore.fromDataStore(dataStore)
}

private class FakeListingApi : ListingApi {
    var shouldFail: Boolean = false

    override suspend fun getListingDetail(id: String): ListingDetailDto {
        if (shouldFail) throw IOException("network down")
        return ListingDetailDto(id = id, categoryId = "cat")
    }

    override suspend fun getCategories(): List<CatalogItemDto> = emptyList()

    override suspend fun getBrands(categoryId: String?): List<CatalogItemDto> = emptyList()

    override suspend fun createCategory(body: ListingApi.CreateCategoryIn): CatalogItemDto =
        error("Not used")

    override suspend fun createBrand(body: ListingApi.CreateBrandIn): CatalogItemDto =
        error("Not used")

    override suspend fun searchListings(
        q: String?,
        categoryId: String?,
        brandId: String?,
        minPrice: Int?,
        maxPrice: Int?,
        nearLat: Double?,
        nearLon: Double?,
        radiusKm: Double?,
        mine: Boolean?,
        sellerId: String?,
        page: Int?,
        pageSize: Int?
    ): SearchListingsResponse = error("Not used")

    override suspend fun createListing(body: CreateListingRequest): ListingDetailDto =
        error("Not used")

    override suspend fun attachListingImage(
        id: String,
        body: com.techmarketplace.data.remote.api.AttachListingImageIn
    ): com.techmarketplace.data.remote.api.AttachListingImageOut = error("Not used")

    override suspend fun deleteListing(id: String) {
        error("Not used")
    }
}
