package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.model.QuoteReference
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CompatThreadTreeSupportTest {
    private fun post(no: Int, parent: Int? = null, body: String = "body$no") =
        CompatPostSnapshot(position = no, postNo = "$no", timestamp = "", messageHtml = body,
            quoteReferences = parent?.let { listOf(QuoteReference(">>$it", listOf("$it"))) }.orEmpty())

    @Test fun settingDefaultsFlatAndRemainsSeparateFromModernMode() {
        val entry = compatSettingsGroups("thread").flatMap { it.second }
            .single { it.preferenceKey == COMPAT_THREAD_DISPLAY_MODE_KEY }
        assertEquals("通常表示", entry.summary)
        assertEquals(listOf("通常表示", "ツリー表示"), compatPreferenceOptions("thread", entry))
        assertFalse(emptyMap<String, String>().compatThreadTreeEnabled())
        val key = compatPreferenceStorageKey("thread", COMPAT_THREAD_DISPLAY_MODE_KEY)
        assertEquals("compat.thread.threadDisplayMode", key)
        for ((label, stored) in listOf("通常表示" to "flat", "ツリー表示" to "tree")) {
            assertEquals(stored, compatPreferenceStoredValue(COMPAT_THREAD_DISPLAY_MODE_KEY, label))
            assertEquals(label, compatPreferenceDisplayValue(COMPAT_THREAD_DISPLAY_MODE_KEY, stored))
            assertEquals(stored == "tree", mapOf(key to stored).compatThreadTreeEnabled())
        }
        assertTrue(compatSettingsGroups("thread", modernPresentation = true).flatMap { it.second }
            .none { it.preferenceKey == COMPAT_THREAD_DISPLAY_MODE_KEY })
    }

    @Test fun repliesFollowTheirParentAndFlatRestoresOriginalOrderWithoutMutatingPosts() = runBlocking {
        val posts = listOf(post(1), post(2), post(3, 1), post(4, 3), post(5, 1))
        val tree = buildCompatThreadDisplay(posts, tree = true)
        assertEquals(listOf("1", "3", "4", "5", "2"), tree.posts.map { it.postNo })
        assertEquals(listOf(0, 1, 2, 1, 0), tree.posts.map { tree.depthByPostNo[it.postNo] })
        tree.posts.forEach { assertSame(posts.single { source -> source.postNo == it.postNo }, it) }
        val flat = buildCompatThreadDisplay(posts, tree = false)
        assertSame(posts, flat.posts)
        assertTrue(flat.depthByPostNo.isEmpty())
    }

    @Test fun missingFilteredSelfAndFutureParentsRemainRootsAndMultipleQuotesAppearOnce() = runBlocking {
        val posts = listOf(post(1, 9), post(2, 2), post(3, 4), post(4),
            post(5).copy(quoteReferences = listOf(QuoteReference("multiple", listOf("9", "2", "1")))))
        val tree = buildCompatThreadDisplay(posts, tree = true)
        assertEquals(listOf("1", "2", "5", "3", "4"), tree.posts.map { it.postNo })
        assertEquals(mapOf("1" to 0, "2" to 0, "5" to 1, "3" to 0, "4" to 0), tree.depthByPostNo)
    }

    @Test fun oldSnapshotsResolveNumberAndTextQuotesWhileRedactedContentIsNotParsed() = runBlocking {
        val posts = listOf(post(1, body = "quoted body"), post(2),
            post(3, body = "&gt;No.1<br>reply"),
            post(4, body = "&gt;quoted body<br>text reply"),
            post(5, body = "&gt;No.1").copy(isContentRedacted = true))
        val tree = buildCompatThreadDisplay(posts, tree = true)
        assertEquals(listOf("1", "3", "4", "2", "5"), tree.posts.map { it.postNo })
        assertEquals(0, tree.depthByPostNo["5"])
    }

    @Test fun deepTreeCapsIndentationAndRetainsEveryPost() = runBlocking {
        val posts = (1..100).map { post(it, if (it == 1) null else it - 1) }
        val tree = buildCompatThreadDisplay(posts, tree = true)
        assertEquals(posts, tree.posts)
        assertEquals(12, tree.depthByPostNo["100"])
    }
}
