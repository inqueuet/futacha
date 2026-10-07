package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.image.isTutorialImageUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PostInteractionSupportTest {
    private fun post(id: String, vararg parents: String) = Post(id = id, author = null, subject = null,
        timestamp = "", messageHtml = "body", imageUrl = null, thumbnailUrl = null,
        quoteReferences = listOf(QuoteReference("quote", parents.toList())))

    @Test fun relatedPostsIncludeBothDirectionsAndSelfOnceWithoutUnrelatedOrMissingTargets() {
        val posts = listOf(post("1"), post("2"), post("3", "1", "1", "missing"), post("4", "3"), post("5"))
        assertEquals(listOf("1", "3", "4"), relatedThreadPosts(posts[2], posts).map { it.id })
        assertEquals(listOf("2"), relatedThreadPosts(posts[1], posts).map { it.id })
    }

    @Test fun compatibilityCachedTextQuotesResolveBothDirectionsAndPreserveOrder() = runBlocking<Unit> {
        val posts = listOf(
            CompatPostSnapshot(0, "1", timestamp = "", messageHtml = "parent"),
            CompatPostSnapshot(1, "2", timestamp = "", messageHtml = "unrelated"),
            CompatPostSnapshot(2, "3", timestamp = "", messageHtml = "&gt;No.1<br>reply"),
            CompatPostSnapshot(3, "4", timestamp = "", messageHtml = "&gt;No.3<br>child"),
            CompatPostSnapshot(4, "5", timestamp = "", messageHtml = "&gt;No.3", isContentRedacted = true))
        assertEquals(listOf("1", "3", "4"), relatedCompatPosts("3", posts).map { it.postNo })
        assertTrue(relatedCompatPosts("absent", posts).isEmpty())
        assertEquals(listOf("3", "4"), relatedCompatPosts("3", posts.drop(1)).map { it.postNo })
    }

    @Test fun newTapBehaviorIsOptionalSharedAndDoesNotChangeTheLegacyDefault() {
        val entry = compatSettingsGroups("control").flatMap { it.second }.single { it.preferenceKey == POST_TAP_BEHAVIOR_KEY }
        assertEquals("従来の操作", entry.summary)
        assertEquals("legacy", compatPreferenceStoredValue(POST_TAP_BEHAVIOR_KEY, entry.summary))
        assertEquals("related", compatPreferenceStoredValue(POST_TAP_BEHAVIOR_KEY, compatPreferenceOptions("control", entry)[1]))
        assertEquals("compat.control.controlPostTapBehavior", compatPreferenceStorageKey("control", POST_TAP_BEHAVIOR_KEY))
        assertTrue(compatSettingsGroups("control", true).flatMap { it.second }.contains(entry))
    }

    @Test fun localNgAndReportFailuresAreIndependentAndNoUncheckedNgRuns() = runBlocking<Unit> {
        for (sendFails in listOf(false, true)) for (ngFails in listOf(false, true)) {
            var sent = 0; var registered = 0
            val message = sendReportAndOptionalNg(true,
                send = { sent++; if (sendFails) error("offline") },
                registerNg = { registered++; if (ngFails) error("disk full") })
            assertEquals(1, sent); assertEquals(1, registered)
            assertTrue(message.contains(if (sendFails) "送信できませんでした" else "送信しました"))
            assertTrue(message.contains(if (ngFails) "NGの登録に失敗" else "NGに登録しました"))
        }
        sendReportAndOptionalNg(false, send = {}, registerNg = { fail("NG must be explicitly selected") })
    }

    @Test fun cancellationDoesNotRegisterAnUnrequestedFollowup() = runBlocking<Unit> {
        assertFailsWith<CancellationException> {
            sendReportAndOptionalNg(true, send = { throw CancellationException() },
                registerNg = { fail("cancelled") })
        }
    }

    @Test fun tutorialFetcherOnlyClaimsTheExampleBoardImagePaths() {
        assertTrue(isTutorialImageUrl("https://www.example.com/b/thumb/1762576973515s.jpg"))
        assertTrue(isTutorialImageUrl("https://www.example.com/t/cat/1762436883775s.jpg"))
        assertTrue(isTutorialImageUrl("https://www.example.com/b/src/1762576973515.png"))
        assertFalse(isTutorialImageUrl("https://may.2chan.net/b/src/1762576973515.png"))
        assertFalse(isTutorialImageUrl("https://example.com.evil/b/src/1762576973515.png"))
        assertFalse(isTutorialImageUrl("https://www.example.com/b/res/123.htm"))
    }
}
