package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.compatReplyCount
import com.valoser.futacha.shared.compat.toCompatPostSnapshot
import com.valoser.futacha.shared.compat.toThreadPage
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.resolveHistoryReplyCount
import com.valoser.futacha.shared.model.sitePostCount
import com.valoser.futacha.shared.ui.futaber.FutaberHeaderPartKind
import com.valoser.futacha.shared.ui.futaber.futaberPostHeaderParts
import kotlin.test.Test
import kotlin.test.assertEquals

class AimogeCountAndNumberTest {
    private fun post(id: String, number: Int? = null) =
        Post(id = id, author = null, subject = null, timestamp = "", messageHtml = "x", imageUrl = null, thumbnailUrl = null, displayNumber = number)
    private fun page(posts: List<Post>, maxSeq: Long? = null, truncated: Boolean = false) =
        ThreadPage("t", null, null, null, posts, isTruncated = truncated, maxSeq = maxSeq)

    @Test fun anAimogeThreadCountsMaxSeqPlusOneAndNeverFalls() {
        val gaps = page(listOf(post("10", 0), post("12", 2), post("15", 5)), maxSeq = 5)
        assertEquals(6, gaps.sitePostCount())
        assertEquals(5, gaps.compatReplyCount())
        assertEquals(6, gaps.resolveHistoryReplyCount(null))
        assertEquals(8, gaps.resolveHistoryReplyCount(8))
    }

    @Test fun aFutabaThreadCountsAsBefore() {
        val futaba = page(listOf(post("1"), post("2")))
        assertEquals(2, futaba.sitePostCount())
        assertEquals(1, futaba.compatReplyCount())
        assertEquals(2, futaba.resolveHistoryReplyCount(9))
        assertEquals(9, page(listOf(post("1")), truncated = true).resolveHistoryReplyCount(9))
    }

    @Test fun theHeaderShowsTheSeqAndAFutabaPostItsOrdinal() {
        val ordinal = { p: Post -> futaberPostHeaderParts(1, p, null, null, 0).first { it.kind == FutaberHeaderPartKind.Ordinal }.text }
        assertEquals("5", ordinal(post("15", 5)))
        assertEquals("1", ordinal(post("2")))
        val snapshot = post("15", 5).toCompatPostSnapshot(1)
        assertEquals(5, snapshot.displayNumber)
        assertEquals(5, CompatThreadSnapshot("k", 0, 0, posts = listOf(snapshot)).toThreadPage("t").posts.single().displayNumber)
        assertEquals(null, post("2").toCompatPostSnapshot(1).displayNumber)
    }
}

class AimogeExpiryLabelTest {
    private val utc = kotlinx.datetime.TimeZone.UTC
    private val at = kotlin.time.Instant.parse("2026-11-11T13:05:00Z").toEpochMilliseconds()

    @Test fun theLabelIsTheTimeThenSoonThenNothing() {
        assertEquals("11/11(水) 13:05頃消えます", com.valoser.futacha.shared.network.aimoge.aimogeExpiryLabel(at, at - 3_600_000, utc))
        assertEquals("もうすぐ消えます", com.valoser.futacha.shared.network.aimoge.aimogeExpiryLabel(at, at - 600_000, utc))
        assertEquals(null, com.valoser.futacha.shared.network.aimoge.aimogeExpiryLabel(at, at, utc))
        assertEquals(null, com.valoser.futacha.shared.network.aimoge.aimogeExpiryLabel(null, at, utc))
    }
}
