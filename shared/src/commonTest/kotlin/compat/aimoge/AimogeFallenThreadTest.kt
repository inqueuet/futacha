package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.shouldPreferLocalCompatSnapshot
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.ui.compat.CompatThreadFetchSource
import com.valoser.futacha.shared.ui.compat.loadCompatThreadWithFallback
import com.valoser.futacha.shared.ui.compat.resolveCompatThreadDeadState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** AM-B3b: fallen あいもげ threads keep their posts and are treated as gone; removed posts are not brought back. */
class AimogeFallenThreadTest {
    private fun page(archived: Boolean) = ThreadPage(
        threadId = "t", boardTitle = null, expiresAtLabel = null, deletedNotice = null, posts = emptyList(), archived = archived
    )

    @Test
    fun aGoneThreadAsksTheSiteArchiveOnceBeforeTheOtherArchives() = runTest {
        var siteCalls = 0
        var archiveCalls = 0
        val result = loadCompatThreadWithFallback(
            sourceUrl = "https://nijiurachan.net/b/ai2/thread/u",
            cacheEnabled = false,
            cacheBaseUrl = null,
            archiveLoader = { archiveCalls++; error("not reached") },
            siteArchiveLoader = { siteCalls++; page(archived = true) },
            loader = { throw NetworkException("HTTP 404", statusCode = 404) }
        ).getOrThrow()
        assertEquals(1, siteCalls)
        assertEquals(0, archiveCalls)
        assertEquals(CompatThreadFetchSource.ARCHIVE, result.source)
        assertTrue(resolveCompatThreadDeadState(false, result))
    }

    @Test
    fun aLiveAnswerThatIsArchivedIsTreatedAsGone() = runTest {
        var siteCalls = 0
        val result = loadCompatThreadWithFallback(
            sourceUrl = "https://nijiurachan.net/b/ai2/thread/u",
            cacheEnabled = false,
            cacheBaseUrl = null,
            siteArchiveLoader = { siteCalls++; null },
            loader = { page(archived = true) }
        ).getOrThrow()
        assertEquals(0, siteCalls)
        assertTrue(result.primaryThreadGone)
        assertTrue(resolveCompatThreadDeadState(false, result))
    }

    @Test
    fun aFutabaLiveAnswerStaysAlive() = runTest {
        val result = loadCompatThreadWithFallback(
            sourceUrl = "https://may.2chan.net/b/res/1.htm",
            cacheEnabled = false,
            cacheBaseUrl = null,
            loader = { page(archived = false) }
        ).getOrThrow()
        assertFalse(result.primaryThreadGone)
        assertFalse(resolveCompatThreadDeadState(true, result))
    }

    private fun snap(count: Int) = CompatThreadSnapshot(
        tabKey = "k", revision = 0, fetchedAtEpochMillis = 0,
        posts = (1..count).map { CompatPostSnapshot(position = it, postNo = it.toString(), timestamp = "", messageHtml = "") }
    )

    @Test
    fun aLongerDeviceCopyDoesNotBringBackRemovedPostsOnAimoge() {
        assertTrue(shouldPreferLocalCompatSnapshot(snap(3), snap(2)))
        assertFalse(shouldPreferLocalCompatSnapshot(snap(3), snap(2), serverDropsRemovedPosts = true))
        assertTrue(shouldPreferLocalCompatSnapshot(snap(3), snap(0), serverDropsRemovedPosts = true))
    }
}
