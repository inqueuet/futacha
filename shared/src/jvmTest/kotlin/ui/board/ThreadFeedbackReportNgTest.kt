package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.util.JvmFileSystem
import com.valoser.futacha.shared.ui.futaber.*
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.*

class ThreadFeedbackReportNgTest {
    private val board = BoardSummary("may", "虹裏", "二次元", "https://may.2chan.net/b/", "")
    private val url = "https://may.2chan.net/b/res/123.htm"
    private fun post(id: String) = Post(id = id, author = null, subject = null, timestamp = "", messageHtml = "本文", imageUrl = null, thumbnailUrl = null)

    @Test fun urlScopePersistsWithoutCreatingTabsAndOnlyHidesTheChosenPostInTheChosenThread() = runBlocking {
        val dir = Files.createTempDirectory("feedback-ng").toFile()
        val fs = JvmFileSystem(dir)
        var store = DesktopCompatibilityStore(fs)
        try {
            store.initialize()
            registerReportedThreadPostNg(store, board, "123", "456")
            assertTrue(store.tabs.first().isEmpty())
            assertNull(store.workspace.first().activeTabKey)
            val rules = store.ngRules.first()
            assertEquals(listOf(url), rules.map { it.scopeKey })
            val prepared = futaberNgPrepare(emptyList(), emptyList(), rules, compatTabKey(url), compatBoardKey(board.url))
            assertEquals(setOf("456"), futaberNgEvaluate(listOf(post("456"), post("789")), prepared))
            val other = futaberNgPrepare(emptyList(), emptyList(), rules, compatTabKey(url.replace("123", "124")), compatBoardKey(board.url))
            assertTrue(futaberNgEvaluate(listOf(post("456")), other).isEmpty())
            val otherBoard = futaberNgPrepare(emptyList(), emptyList(), rules, compatTabKey(url.replace("may.", "img.")), compatBoardKey("https://img.2chan.net/b/"))
            assertTrue(futaberNgEvaluate(listOf(post("456")), otherBoard).isEmpty())
            store.close()
            store = DesktopCompatibilityStore(fs)
            store.initialize()
            assertEquals(rules, store.ngRules.first())
            assertTrue(store.tabs.first().isEmpty())
            store.deleteBoard(compatBoardKey(board.url))
            assertTrue(store.ngRules.first().isEmpty())
        } finally { store.close(); dir.deleteRecursively() }
    }

    @Test fun urlScopeRequiresAnExistingBoardAndIsNotAllowedForOtherRuleKinds() {
        val boardKeys = setOf(compatBoardKey(board.url))
        assertTrue(isCompatNgScopeValid(CompatNgKind.THREAD_POST_NO, url, boardKeys, emptySet()))
        assertFalse(isCompatNgScopeValid(CompatNgKind.THREAD_POST_NO, url, emptySet(), emptySet()))
        assertFalse(isCompatNgScopeValid(CompatNgKind.THREAD_WORD, url, boardKeys, emptySet()))
        assertFalse(isCompatNgScopeValid(CompatNgKind.THREAD_POST_NO, "https://example.com/res/123.htm", boardKeys, emptySet()))
        assertFalse(isCompatNgScopeValid(CompatNgKind.THREAD_POST_NO, "$url?x=1", boardKeys, emptySet()))
    }

    @Test fun uncheckedDoesNotRegisterAndPartialFailuresAreReportedSeparately() = runBlocking {
        var sent = 0; var registered = 0
        val unchecked = sendReportAndOptionalNg(false, { sent++ }, { registered++ })
        assertEquals(1, sent); assertEquals(0, registered); assertFalse(unchecked.contains("NG"))
        val failedSend = sendReportAndOptionalNg(true, { error("回線エラー") }, { registered++ })
        assertTrue(failedSend.contains("送信できません")); assertTrue(failedSend.contains("NGに登録しました")); assertEquals(1, registered)
        val failedNg = sendReportAndOptionalNg(true, {}, { error("保存エラー") })
        assertTrue(failedNg.contains("送信しました")); assertTrue(failedNg.contains("NGの登録に失敗"))
    }

    @Test fun cancellationDoesNotRegisterNg() = runBlocking {
        var registered = false
        assertFailsWith<CancellationException> {
            sendReportAndOptionalNg(true, { throw CancellationException() }, { registered = true })
        }
        assertFalse(registered)
    }
}
