package com.valoser.futacha.shared.compat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The switches for あいもげ (nijiurachan.net). Every build keeps [sitesEnabled] false until AM-B6; while it is false an
 * あいもげ address is handled exactly as before (the site functions return null and [boardSiteOf] says FUTABA).
 * Tests that set a value must put it back in `finally`.
 */
/** Switch for まとめカタログ (MIX). Off by default: every entry point stays hidden until a host turns it on. */
object MixAvailability {
    var enabled: Boolean by mutableStateOf(false)
}

object AimogeAvailability {
    /** Whether あいもげ boards are shown and fetched. */
    var sitesEnabled: Boolean = false

    /** Attachments and new threads (not yet checked by a person's own post). */
    var unverifiedPostingEnabled: Boolean = false

    /** del (not yet checked by a person's own post). */
    var unverifiedDelEnabled: Boolean = false
}

fun aimogeUnverifiedPostingEnabled(): Boolean = AimogeAvailability.sitesEnabled && AimogeAvailability.unverifiedPostingEnabled

fun aimogeUnverifiedDelEnabled(): Boolean = AimogeAvailability.sitesEnabled && AimogeAvailability.unverifiedDelEnabled

enum class BoardSite { FUTABA, AIMOGE }

const val AIMOGE_HOST = "nijiurachan.net"
const val AIMOGE_API_HOST = "api.nijiurachan.net"

data class SiteCanonicalBoard(
    val site: BoardSite,
    val canonicalUrl: String,
    /** The board's path without slashes: `b` for may/b, the slug for あいもげ. */
    val boardPath: String
)

data class SiteCanonicalThread(
    val site: BoardSite,
    val canonicalUrl: String,
    val canonicalBoardUrl: String,
    val boardPath: String,
    /** The Futaba thread number, or the あいもげ thread UUID (lowercase). */
    val threadId: String,
    /** The `#post-{UUID}` of an あいもげ address, lowercase, else null. */
    val postId: String? = null
)

private const val SITE_URL_MAX_CHARS = 8_192
private val aimogeUrlRegex = Regex("^https://([^/?#]+)(/[^?#]*)?(\\?[^#]*)?(#.*)?$", RegexOption.IGNORE_CASE)
private val aimogeSlugPattern = "[a-z0-9][a-z0-9_-]{0,31}"
private val aimogeUuidPattern = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
private val aimogeBoardPathRegex = Regex("^/b/($aimogeSlugPattern)(?:/(?:futaba\\.php|futaba\\.htm)?)?$")
private val aimogeThreadPathRegex = Regex("^/b/($aimogeSlugPattern)/thread/($aimogeUuidPattern)/?$")
private val aimogeUuidRegex = Regex("^$aimogeUuidPattern$")
private val aimogePostFragmentRegex = Regex("^#post-($aimogeUuidPattern)$")

private data class AimogeParts(val path: String, val fragment: String)

/**
 * The path and fragment of an https address on nijiurachan.net itself, or null. The API host, other hosts
 * (`nijiurachan.net.evil.com`), ports, userinfo, `%`-escapes, `\`, whitespace and non-ASCII are refused.
 */
private fun aimogeParts(url: String): AimogeParts? {
    if (url.length > SITE_URL_MAX_CHARS) return null
    val trimmed = url.trim()
    if (trimmed.any { it.code >= 0x80 || it == '\\' || it.isWhitespace() || it.isISOControl() }) return null
    val match = aimogeUrlRegex.matchEntire(trimmed) ?: return null
    if (!match.groupValues[1].equals(AIMOGE_HOST, ignoreCase = true)) return null
    val path = match.groupValues[2].ifEmpty { "/" }
    if ('%' in path) return null
    return AimogeParts(path = path.lowercase(), fragment = match.groupValues[4].lowercase())
}

/** The canonical あいもげ board address of [url] without reading [AimogeAvailability.sitesEnabled], or null. */
fun aimogeBoardShapeOf(url: String): String? {
    val parts = aimogeParts(url) ?: return null
    val slug = aimogeBoardPathRegex.matchEntire(parts.path)?.groupValues?.get(1)
        ?: aimogeThreadPathRegex.matchEntire(parts.path)?.groupValues?.get(1)
        ?: return null
    return "https://$AIMOGE_HOST/b/$slug/"
}

internal const val AIMOGE_NEWS_BOARD_SLUG = "news"

internal fun isAimogeNewsBoard(url: String): Boolean =
    aimogeBoardShapeOf(url) == "https://$AIMOGE_HOST/b/$AIMOGE_NEWS_BOARD_SLUG/"

/** The canonical あいもげ board address of a board [url] (not a thread) without reading [AimogeAvailability.sitesEnabled]. */
fun aimogeBoardOnlyShapeOf(url: String): String? {
    val parts = aimogeParts(url) ?: return null
    val slug = aimogeBoardPathRegex.matchEntire(parts.path)?.groupValues?.get(1) ?: return null
    return "https://$AIMOGE_HOST/b/$slug/"
}

/**
 * The canonical board address the three stores check a saved board against: the Futaba one, or the あいもげ shape
 * whether あいもげ is on or off (an あいもげ board restored while it is off is kept and hidden, never dropped).
 */
fun storeCanonicalBoardUrlOf(url: String): String? = canonicalizeBoardUrl(url) ?: aimogeBoardOnlyShapeOf(url)

private fun aimogeThreadShapeOf(url: String): SiteCanonicalThread? {
    val parts = aimogeParts(url) ?: return null
    val match = aimogeThreadPathRegex.matchEntire(parts.path) ?: return null
    val slug = match.groupValues[1]
    val uuid = match.groupValues[2]
    val boardUrl = "https://$AIMOGE_HOST/b/$slug/"
    return SiteCanonicalThread(
        site = BoardSite.AIMOGE,
        canonicalUrl = "${boardUrl}thread/$uuid",
        canonicalBoardUrl = boardUrl,
        boardPath = slug,
        threadId = uuid,
        postId = aimogePostFragmentRegex.matchEntire(parts.fragment)?.groupValues?.get(1)
    )
}

/** AIMOGE for a nijiurachan.net address while あいもげ is on, else FUTABA (the Futaba checks decide the rest). */
fun boardSiteOf(url: String): BoardSite =
    if (AimogeAvailability.sitesEnabled && aimogeBoardShapeOf(url) != null) BoardSite.AIMOGE else BoardSite.FUTABA

/** The Futaba board as [canonicalizeBoardUrl] gives it, or the あいもげ board while あいもげ is on, or null. */
fun canonicalizeSiteBoardUrl(url: String): SiteCanonicalBoard? {
    canonicalizeBoardUrl(url)?.let { futaba ->
        return SiteCanonicalBoard(BoardSite.FUTABA, futaba, futaba.substringAfter("://").substringAfter('/').trim('/'))
    }
    if (!AimogeAvailability.sitesEnabled) return null
    val parts = aimogeParts(url) ?: return null
    val slug = aimogeBoardPathRegex.matchEntire(parts.path)?.groupValues?.get(1) ?: return null
    return SiteCanonicalBoard(BoardSite.AIMOGE, "https://$AIMOGE_HOST/b/$slug/", slug)
}

/** The Futaba thread as [canonicalizeThreadUrl] gives it, or the あいもげ thread while あいもげ is on, or null. */
fun canonicalizeSiteThreadUrl(url: String): SiteCanonicalThread? {
    canonicalizeThreadUrl(url)?.let { futaba ->
        return SiteCanonicalThread(
            site = BoardSite.FUTABA,
            canonicalUrl = futaba.canonicalUrl,
            canonicalBoardUrl = futaba.canonicalBoardUrl,
            boardPath = futaba.boardPath,
            threadId = futaba.threadNo
        )
    }
    if (!AimogeAvailability.sitesEnabled) return null
    return aimogeThreadShapeOf(url)
}

/**
 * The address of thread [threadId] on [boardUrl] for an あいもげ board while あいもげ is on, else null (the caller then
 * uses the Futaba `res/{no}.htm` rule). An あいもげ board with a thread id that is not a UUID gives null, never the
 * board's own address.
 */
fun siteThreadUrlOf(boardUrl: String, threadId: String): String? {
    if (!AimogeAvailability.sitesEnabled) return null
    val board = aimogeBoardShapeOf(boardUrl) ?: return null
    val uuid = threadId.trim().lowercase()
    if (!aimogeUuidRegex.matches(uuid)) return null
    return "${board}thread/$uuid"
}

/**
 * The canonical thread address on the canonical board [canonicalBoardUrl]: the あいもげ `thread/{UUID}` while あいもげ is
 * on, else the Futaba `res/{no}.htm` (exactly the string the Futaba-only callers built).
 */
fun siteThreadUrlOnBoard(canonicalBoardUrl: String, threadId: String): String =
    siteThreadUrlOf(canonicalBoardUrl, threadId) ?: "${canonicalBoardUrl.trimEnd('/')}/res/$threadId.htm"

/** A thread id the shared stores accept: a Futaba number, or an あいもげ UUID on an あいもげ board while あいもげ is on. */
fun isSiteThreadId(canonicalBoardUrl: String?, threadId: String): Boolean =
    threadId.isNotEmpty() && threadId.all(Char::isDigit) ||
        (canonicalBoardUrl != null && siteThreadUrlOf(canonicalBoardUrl, threadId) != null)

/** What an address pasted or opened from outside points to on あいもげ. */
sealed interface AimogeLinkTarget {
    data class Board(val canonicalBoardUrl: String) : AimogeLinkTarget
    data class Thread(val thread: SiteCanonicalThread) : AimogeLinkTarget
}

/** The board, thread or `thread#post-{UUID}` that [url] names while あいもげ is on, else null. */
fun aimogeLinkTargetOf(url: String): AimogeLinkTarget? {
    if (!AimogeAvailability.sitesEnabled) return null
    aimogeThreadShapeOf(url)?.let { return AimogeLinkTarget.Thread(it) }
    val parts = aimogeParts(url) ?: return null
    val slug = aimogeBoardPathRegex.matchEntire(parts.path)?.groupValues?.get(1) ?: return null
    return AimogeLinkTarget.Board("https://$AIMOGE_HOST/b/$slug/")
}

/** What a build with あいもげ off says when an あいもげ link is opened from outside. */
const val AIMOGE_UNAVAILABLE_MESSAGE = "あいもげの板は、この版では使えません"

/** What to do with an address opened from outside (the Android link filter, AM-B6). */
enum class AimogeIncomingLink { NOT_AIMOGE, OPEN, UNAVAILABLE }

/**
 * An https `nijiurachan.net/b/{slug}/thread/{UUID}` link opens while あいもげ is on and says [AIMOGE_UNAVAILABLE_MESSAGE]
 * while it is off. Every other address, the approval page `…/app-auth?…` too, is NOT_AIMOGE (left to the browser).
 */
fun aimogeIncomingLinkOf(url: String): AimogeIncomingLink = when {
    aimogeThreadShapeOf(url) == null -> AimogeIncomingLink.NOT_AIMOGE
    AimogeAvailability.sitesEnabled -> AimogeIncomingLink.OPEN
    else -> AimogeIncomingLink.UNAVAILABLE
}

/** `aimoge-{slug}` for an あいもげ board address while あいもげ is on, else null. */
fun aimogeBoardIdOf(url: String): String? =
    if (AimogeAvailability.sitesEnabled) aimogeBoardShapeOf(url)?.let { "aimoge-" + it.removeSuffix("/").substringAfterLast('/') } else null

/** [canonicalizeSiteBoardUrl] as the plain string the Futaba-only callers used. */
fun siteCanonicalBoardUrlOf(url: String): String? = canonicalizeSiteBoardUrl(url)?.canonicalUrl

/**
 * [canonicalizeSiteThreadUrl] in the [CanonicalThreadUrl] shape the Futaba-only callers used; for あいもげ
 * [CanonicalThreadUrl.threadNo] holds the UUID.
 */
fun canonicalizeSiteThreadUrlAsLegacy(url: String): CanonicalThreadUrl? =
    canonicalizeSiteThreadUrl(url)?.let { CanonicalThreadUrl(it.canonicalUrl, it.canonicalBoardUrl, it.boardPath, it.threadId) }

/** False for an あいもげ address while あいもげ is off: kept in the stores, but not shown (AM-B5). */
fun isSiteAddressShown(url: String): Boolean = AimogeAvailability.sitesEnabled || aimogeBoardShapeOf(url) == null

/** The tabs whose thread is shown ([isSiteAddressShown]). */
fun Flow<List<CompatTab>>.shownSiteTabs(): Flow<List<CompatTab>> =
    this.map { tabs: List<CompatTab> -> if (AimogeAvailability.sitesEnabled) tabs else tabs.filter { isSiteAddressShown(it.canonicalUrl) } }
