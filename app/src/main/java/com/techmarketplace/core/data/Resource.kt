package com.techmarketplace.core.data

/**
 * Simple wrapper to model loading, success and error states from repositories/use cases.
 */
sealed class Resource<out T> {
    data object Loading : Resource<Nothing>()

    data class Success<T>(
        val data: T,
        /**
         * True when the payload was fetched from the network (fresh) rather than
         * coming from a potentially stale cache.
         */
        val isFresh: Boolean
    ) : Resource<T>()

    data class Error<T>(
        val throwable: Throwable,
        val cachedData: T? = null
    ) : Resource<T>()
}
