package com.valoser.futacha.shared.model

internal fun CatalogItem.numericId(): Long {
    // An あいもげ id is a UUID: its digits mean nothing, so the sort uses the opening post's number (0 if unknown).
    if (siteOrder != null || isSiteUuidId(id)) return siteOrder ?: 0L
    val digits = id.filter { it.isDigit() }
    return digits.ifEmpty { id }.toLongOrNull() ?: 0L
}

internal fun CatalogItem.hostLabel(): String {
    val withoutScheme = threadUrl.substringAfter("//", threadUrl)
    return withoutScheme.substringBefore("/")
}

private val siteUuidIdRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

private fun isSiteUuidId(id: String): Boolean = siteUuidIdRegex.matches(id)
