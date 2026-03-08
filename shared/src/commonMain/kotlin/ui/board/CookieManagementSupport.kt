package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.network.StoredCookie
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

data class CookieDomainSection(
    val domain: String,
    val cookies: List<StoredCookie>
)

internal fun buildCookieDomainSections(cookies: List<StoredCookie>): List<CookieDomainSection> {
    return cookies
        .groupBy { it.domain }
        .map { (domain, domainCookies) ->
            CookieDomainSection(domain = domain, cookies = domainCookies)
        }
}

internal fun formatCookieExpiresLabel(
    expiresAtMillis: Long?,
    timeZone: TimeZone
): String {
    if (expiresAtMillis == null) return "セッション"
    val instant = Instant.fromEpochMilliseconds(expiresAtMillis)
    val local = instant.toLocalDateTime(timeZone)
    @Suppress("DEPRECATION")
    return "${local.year}/${local.monthNumber.toString().padStart(2, '0')}/${local.dayOfMonth.toString().padStart(2, '0')} ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}
