package com.techmarketplace.core.imageloading

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest

fun prefetchListingImages(context: Context, urls: List<String>) {
    val loader = context.imageLoader
    urls.distinct().forEach { url ->
        val req = ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
        loader.enqueue(req)
    }
}
