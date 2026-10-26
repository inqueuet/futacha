package com.valoser.futacha.shared.compat

private val absoluteUrlRegex = Regex("^(https?)://([^/?#]+)(/[^?#]*)?(?:\\?[^#]*)?(?:#.*)?$", RegexOption.IGNORE_CASE)
private val threadPathRegex = Regex("^/(.+?)/res/([0-9]+)\\.htm/?$", RegexOption.IGNORE_CASE)
private val duplicateSlashRegex = Regex("/{2,}")
private const val COMPAT_URL_MAX_CHARS = 8_192

// The whole authority must be a plain DNS name: `2chan.net` or ASCII labels
// below it. Userinfo (`@`), ports, `%`-escapes and non-ASCII never match.
private val officialFutabaHostRegex = Regex("^(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)*2chan\\.net$")

// ASCII is checked before lowercasing: `lowercase()` maps e.g. the Kelvin sign to `k`.
private fun isOfficialFutabaHost(host: String): Boolean =
    host.all { it.code < 0x80 } && officialFutabaHostRegex.matches(host.lowercase())

/**
 * Splits [url] into scheme, an official Futaba host and the path, or null.
 *
 * Ktor, OkHttp and browsers end the authority at `\` too, and browsers drop
 * tabs and newlines inside a URL. `https://evil.com\@may.2chan.net/b/` looked
 * like a 2chan.net host here but was sent to evil.com, so `\`, whitespace and
 * control characters are refused before the query, where they could change
 * what another parser takes as the host or path.
 */
private fun matchOfficialFutabaUrl(url: String): MatchResult? {
    if (url.length > COMPAT_URL_MAX_CHARS) return null
    val trimmed = url.trim()
    val beforeQuery = trimmed.substringBefore('?').substringBefore('#')
    if (beforeQuery.any { it == '\\' || it.isWhitespace() || it.isISOControl() }) return null
    val match = absoluteUrlRegex.matchEntire(trimmed) ?: return null
    if (!isOfficialFutabaHost(match.groupValues[2])) return null
    return match
}

/**
 * [url] with `https://` when it is an `http://` address on a 2chan.net host, else [url] as is (L100, the decision): iOS
 * cannot post over http, and the official pre-submit step runs only on https. Other hosts keep http (Android still
 * allows cleartext for them).
 */
internal fun upgradeOfficialFutabaHttpUrl(url: String): String {
    val trimmed = url.trim()
    if (!trimmed.startsWith("http://", ignoreCase = true)) return url
    matchOfficialFutabaUrl(trimmed) ?: return url
    return "https://" + trimmed.substring("http://".length)
}

/**
 * The board with its 2chan address over https (L100): としあき(仮)'s stores keep a board's original address for
 * fetching and posting, and an http one (imported before the AppState boards moved to https, or added by the AI)
 * could not post and, on iOS, not load. The canonical address (the board's key) is https already, so it is unchanged.
 */
fun CompatBoard.withOfficialHttps(): CompatBoard {
    val upgraded = upgradeOfficialFutabaHttpUrl(originalUrl)
    return if (upgraded == originalUrl) this else copy(originalUrl = upgraded)
}

data class CanonicalThreadUrl(
    val canonicalUrl: String,
    val canonicalBoardUrl: String,
    val boardPath: String,
    val threadNo: String
)

fun canonicalizeBoardUrl(url: String): String? {
    val match = matchOfficialFutabaUrl(url) ?: return null
    val host = match.groupValues[2].lowercase()
    val normalizedPath = match.groupValues[3]
        .ifBlank { "/" }
        .replace(duplicateSlashRegex, "/")
    val path = normalizedPath.trimEnd('/').let { withoutSlash ->
        val lastSegment = withoutSlash.substringAfterLast('/').lowercase()
        val boardPath = if (lastSegment in setOf("futaba.php", "futaba.htm")) {
            withoutSlash.substringBeforeLast('/', missingDelimiterValue = "")
        } else {
            withoutSlash
        }
        if (boardPath.isBlank()) "/" else "$boardPath/"
    }
    if (path.contains("/res/", ignoreCase = true)) return null
    return "https://$host$path"
}

fun canonicalizeThreadUrl(url: String): CanonicalThreadUrl? {
    val match = matchOfficialFutabaUrl(url) ?: return null
    val host = match.groupValues[2].lowercase()
    val path = match.groupValues[3]
        .replace(duplicateSlashRegex, "/")
    val threadMatch = threadPathRegex.matchEntire(path) ?: return null
    val boardPath = threadMatch.groupValues[1].trim('/')
    val threadNo = threadMatch.groupValues[2]
    return CanonicalThreadUrl(
        canonicalUrl = "https://$host/$boardPath/res/$threadNo.htm",
        canonicalBoardUrl = "https://$host/$boardPath/",
        boardPath = boardPath,
        threadNo = threadNo
    )
}

fun compatBoardKey(canonicalBoardUrl: String): String =
    "compat_board_" + stableCompatHash(canonicalBoardUrl)

fun compatTabKey(canonicalThreadUrl: String): String =
    "compat_tab_" + stableCompatHash(canonicalThreadUrl)

internal fun stableCompatHash(value: String): String {
    require(value.length <= COMPAT_URL_MAX_CHARS) { "Compatibility URL is too long" }
    var hash = 0xcbf29ce484222325UL
    value.encodeToByteArray().forEach { byte ->
        hash = (hash xor byte.toUByte().toULong()) * 0x100000001b3UL
    }
    return hash.toString(16).padStart(16, '0')
}
