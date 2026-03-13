package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.network.StoredCookie
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

data class CookieDomainSection(
    val domain: String,
    val cookies: List<StoredCookie>
)

internal data class CookieReloadState(
    val reloadGeneration: Long,
    val isLoading: Boolean
)

internal sealed interface CookieManagementContentState {
    data object Loading : CookieManagementContentState
    data object Empty : CookieManagementContentState
    data class Data(val sections: List<CookieDomainSection>) : CookieManagementContentState
}

internal fun buildCookieDomainSections(cookies: List<StoredCookie>): List<CookieDomainSection> {
    return cookies
        .groupBy { it.domain }
        .map { (domain, domainCookies) ->
            CookieDomainSection(domain = domain, cookies = domainCookies)
        }
}

internal fun beginCookieReload(
    reloadGeneration: Long
): CookieReloadState {
    return CookieReloadState(
        reloadGeneration = reloadGeneration + 1L,
        isLoading = true
    )
}

internal fun applyCookieReloadResult(
    currentGeneration: Long,
    requestGeneration: Long,
    cookies: List<StoredCookie>,
    isLoading: Boolean
): Pair<List<StoredCookie>, Boolean> {
    return if (currentGeneration == requestGeneration) {
        cookies to false
    } else {
        cookies to isLoading
    }
}

internal fun resolveCookieManagementContentState(
    isLoading: Boolean,
    cookies: List<StoredCookie>
): CookieManagementContentState {
    return when {
        isLoading -> CookieManagementContentState.Loading
        cookies.isEmpty() -> CookieManagementContentState.Empty
        else -> CookieManagementContentState.Data(buildCookieDomainSections(cookies))
    }
}

internal fun shouldShowCookieClearAllAction(cookies: List<StoredCookie>): Boolean {
    return cookies.isNotEmpty()
}

internal fun buildCookieDeleteMessage(name: String): String {
    return "削除しました: $name"
}

internal fun buildCookieClearAllMessage(): String {
    return "すべてのCookieを削除しました"
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
