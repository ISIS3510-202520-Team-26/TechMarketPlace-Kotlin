package com.techmarketplace.presentation.common.ui

import java.util.Locale

fun formatPrice(priceCents: Int, currency: String): String {
    val locale = Locale.getDefault()
    val normalizedCurrency = currency.takeIf { it.isNotBlank() }?.uppercase(locale)
    val formatted = String.format(locale, "%,d", priceCents)
    return when (normalizedCurrency) {
        null -> formatted
        "USD" -> "$$formatted"
        else -> "$normalizedCurrency $formatted"
    }
}

fun cacheKeyFrom(url: String): String = url.substringBefore('?')
