package com.techmarketplace.core.network

/**
 * Normalizes backend URLs so emulator instances can reach localhost hosts exposed by the API.
 */
fun fixEmulatorHost(url: String?): String? = url
    ?.replace("http://localhost", "http://10.0.2.2")
    ?.replace("http://127.0.0.1", "http://10.0.2.2")
