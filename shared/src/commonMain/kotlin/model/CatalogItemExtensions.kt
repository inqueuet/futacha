package com.valoser.futacha.shared.model

internal fun CatalogItem.numericId(): Long {
    val digits = id.filter { it.isDigit() }
    return digits.ifEmpty { id }.toLongOrNull() ?: 0L
}

internal fun CatalogItem.hostLabel(): String {
    val withoutScheme = threadUrl.substringAfter("//", threadUrl)
    return withoutScheme.substringBefore("/")
}
