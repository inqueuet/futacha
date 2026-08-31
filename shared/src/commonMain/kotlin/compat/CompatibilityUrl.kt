package com.valoser.futacha.shared.compat

private val absoluteUrlRegex = Regex("^(https?)://([^/?#]+)(/[^?#]*)?(?:\\?[^#]*)?(?:#.*)?$", RegexOption.IGNORE_CASE)
private val threadPathRegex = Regex("^/(.+?)/res/([0-9]+)\\.htm/?$", RegexOption.IGNORE_CASE)
private val duplicateSlashRegex = Regex("/{2,}")
private const val COMPAT_URL_MAX_CHARS = 8_192

private fun isOfficialFutabaHost(host: String): Boolean =
    host == "2chan.net" || host.endsWith(".2chan.net")

data class CanonicalThreadUrl(
    val canonicalUrl: String,
    val canonicalBoardUrl: String,
    val boardPath: String,
    val threadNo: String
)

fun canonicalizeBoardUrl(url: String): String? {
    if (url.length > COMPAT_URL_MAX_CHARS) return null
    val match = absoluteUrlRegex.matchEntire(url.trim()) ?: return null
    val host = match.groupValues[2].lowercase()
    if (!isOfficialFutabaHost(host)) return null
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
    if (url.length > COMPAT_URL_MAX_CHARS) return null
    val match = absoluteUrlRegex.matchEntire(url.trim()) ?: return null
    val host = match.groupValues[2].lowercase()
    if (!isOfficialFutabaHost(host)) return null
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
