package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException

/**
 * [BoardRepository.getArchivedThreadContent] for a thread that is gone, or null (Futaba repositories answer null;
 * a failure is logged and treated as no copy so the existing fallback still runs).
 */
internal suspend fun siteArchivedThreadContent(repository: BoardRepository, boardUrl: String, threadId: String): ThreadPageContent? =
    try {
        repository.getArchivedThreadContent(boardUrl, threadId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Logger.w("SiteArchive", "The site's archived copy could not be read: ${failure.message}")
        null
    }

/** The site-archive step of としあき(仮)'s thread loading: only for an あいもげ thread address, else null. */
internal fun compatSiteArchiveLoader(repository: BoardRepository, threadUrl: String): (suspend () -> ThreadPage?)? {
    val thread = canonicalizeSiteThreadUrl(threadUrl)?.takeIf { it.site == BoardSite.AIMOGE } ?: return null
    return { siteArchivedThreadContent(repository, thread.canonicalBoardUrl, thread.threadId)?.page }
}

/**
 * True when [page] is a thread that has fallen off (あいもげ answers fallen threads with their posts and
 * [ThreadPage.archived]); the screens then treat it as they treat a Futaba 404/410 and show 「落ちました」, keeping
 * the posts.
 */
fun ThreadPage.isFallenSitePage(): Boolean = archived
