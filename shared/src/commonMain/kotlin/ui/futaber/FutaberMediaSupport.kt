package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.ui.compat.CompatibilityPalette
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient

/**
 * What the shared image gallery and viewer need besides the thread itself. The
 * ふたばー runtime hands it over; without a compatibility store there is no gallery.
 */
internal class FutaberMediaServices(
    val store: CompatibilityStore,
    val httpClient: HttpClient?,
    val fileSystem: FileSystem?,
    val cookieRepository: CookieRepository?
)

/**
 * The gallery and viewer are shared screens that read their colours from a
 * [CompatibilityPalette]. This maps the ふたばー roles onto it, so every text role of
 * the shared screens keeps the contrast the ふたばー theme guarantees.
 */
internal fun futaberMediaPalette(colors: FutaberColors): CompatibilityPalette = CompatibilityPalette(
    chrome = colors.bar,
    chromeContent = colors.icon,
    statusBarChrome = colors.bar,
    background = colors.background,
    divider = colors.separator,
    text = colors.body,
    headerSubject = colors.accent,
    headerAuthor = colors.quote,
    headerEmail = colors.link,
    headerSelfPost = colors.link,
    headerSelfQuote = colors.quote,
    headerQuoteAdd = colors.quote,
    headerSubtext = colors.meta,
    identityTotal = colors.accent,
    saidane = colors.accent,
    saidaneMax = colors.accent,
    fileName = colors.link,
    bodyLink = colors.link,
    bodyQuote = colors.quote,
    bodyDropSoon = colors.accent,
    bodyIp = colors.link,
    bodyErased = colors.meta,
    menuSurface = colors.bar,
    dialogSurface = colors.bar,
    uiPrimaryText = colors.body,
    uiSecondaryText = colors.meta,
    searchResultBackground = colors.catalogGap,
    searchTextHighlight = colors.catalogGap,
    accent = colors.link,
    inputCursor = colors.link,
    closedThreadUndoAction = colors.link,
    loadingIcon = colors.accent,
    loadingProgress = colors.accent
)

/**
 * The shared screens are keyed by a tab. ふたばー keeps no such tab for a thread that is just
 * open, so this one is only built for the screens and is never stored.
 */
internal fun futaberMediaTab(board: BoardSummary, ref: FutaberThreadRef, snapshotRevision: Long): CompatTab {
    val url = futaberCompatThreadUrl(board, ref.threadId)
    return CompatTab(
        key = compatTabKey(url),
        canonicalUrl = url,
        originalUrl = url,
        boardKey = futaberCompatBoardKey(board),
        boardName = board.name,
        threadNo = ref.threadId,
        title = ref.title,
        thumbnailUrl = ref.thumbnailUrl.ifBlank { null },
        replyCount = ref.replyCount,
        checkedReplyCount = ref.replyCount,
        insertedAtEpochMillis = 0L,
        contentUpdatedAtEpochMillis = 0L,
        snapshotRevision = snapshotRevision
    )
}

/** Posts that carry an image or a video; zero keeps the gallery entry disabled. */
internal fun futaberMediaPostCount(posts: List<Post>): Int = posts.count { !it.imageUrl.isNullOrBlank() }
