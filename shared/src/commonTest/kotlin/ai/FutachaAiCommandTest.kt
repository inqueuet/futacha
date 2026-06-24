package com.valoser.futacha.shared.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutachaAiCommandTest {
    @Test
    fun supportedActionsContainMvpCommandSet() {
        assertTrue(FutachaAiAction.supportedActions.size >= 50)
        assertNotNull(FutachaAiAction.fromId("open_board"))
        assertNotNull(FutachaAiAction.fromId("save_current_thread"))
        assertNotNull(FutachaAiAction.fromId("draft_thread"))
        assertNotNull(FutachaAiAction.fromId("thread_summary_on"))
        assertNotNull(FutachaAiAction.fromId("ai_post_filter_on"))
    }

    @Test
    fun commandReceptionDescribesSafeAndConfirmActions() {
        val safe = describeFutachaAiCommandReception("open_board")
        val confirm = describeFutachaAiCommandReception("save_current_thread")

        assertNotNull(safe)
        assertEquals("accepted_pending_execution", safe.status)
        assertFalse(safe.requiresConfirmation)
        assertEquals("open_board", safe.actionId)
        assertTrue(safe.message.contains("受け付け"))

        assertNotNull(confirm)
        assertEquals("accepted_pending_user_action", confirm.status)
        assertTrue(confirm.requiresConfirmation)
        assertEquals("save_current_thread", confirm.actionId)
        assertTrue(confirm.message.contains("スレ保存"))
        assertTrue(confirm.message.contains("アプリ内で確認"))
    }

    @Test
    fun commandReceptionMarksOpenOnlyActionsAsPendingForeground() {
        val reception = describeFutachaAiCommandReception("refresh_current_thread")

        assertNotNull(reception)
        assertEquals("accepted_pending_foreground", reception.status)
        assertFalse(reception.requiresConfirmation)
        assertTrue(reception.message.contains("対象画面"))
    }

    @Test
    fun commandReceptionUsesActionSpecificConfirmationReason() {
        val addBoard = describeFutachaAiCommandReception("add_board")
        val deleteBoard = describeFutachaAiCommandReception("delete_board")

        assertNotNull(addBoard)
        assertTrue(addBoard.message.contains("板リスト"))
        assertNotNull(deleteBoard)
        assertTrue(deleteBoard.message.contains("削除"))
    }

    @Test
    fun commandReceptionRejectsUnknownActions() {
        assertNull(describeFutachaAiCommandReception("unknown_action"))
    }

    @Test
    fun actionLookupToleratesCaseAndSeparatorDifferences() {
        assertEquals(FutachaAiAction.SaveCurrentThread, FutachaAiAction.fromId("save_current_thread"))
        assertEquals(FutachaAiAction.SaveCurrentThread, FutachaAiAction.fromId("save-current-thread"))
        assertEquals(FutachaAiAction.SaveCurrentThread, FutachaAiAction.fromId("SaveCurrentThread"))
        assertEquals(FutachaAiAction.OpenThreadFromUrl, FutachaAiAction.fromId("openThreadUrl"))
        assertEquals(FutachaAiAction.OpenGlobalSettings, FutachaAiAction.fromId("open settings"))
        assertEquals(FutachaAiAction.OpenSavedThreads, FutachaAiAction.fromId("saved threads"))
        assertEquals(FutachaAiAction.SearchCatalog, FutachaAiAction.fromId("catalog search"))
        assertEquals(FutachaAiAction.SaveCurrentThread, FutachaAiAction.fromId("save"))
        assertEquals(FutachaAiAction.DraftReply, FutachaAiAction.fromId("reply"))
        assertEquals(FutachaAiAction.EnableThreadSummaryMode, FutachaAiAction.fromId("summary on"))
        assertEquals(FutachaAiAction.EnableAiPostFilter, FutachaAiAction.fromId("post filter on"))
    }

    @Test
    fun parseDeepLinkBuildsCommandWithParameters() {
        val command = parseFutachaAiDeepLink(
            "futacha://ai?action=open_thread&board=b&thread=12345"
        )

        assertNotNull(command)
        assertEquals(FutachaAiAction.OpenThread, command.action)
        assertEquals("b", command.parameter("board"))
        assertEquals("12345", command.parameter("thread"))
    }

    @Test
    fun parameterLookupToleratesCaseAndSeparatorDifferences() {
        val command = parseFutachaAiDeepLink(
            "futacha://ai?action=open_thread&Board-ID=b&ThreadID=12345&delete-key=del"
        )

        assertNotNull(command)
        assertEquals(FutachaAiAction.OpenThread, command.action)
        assertEquals("b", command.parameter("boardId", "board_id"))
        assertEquals("12345", command.parameter("thread_id"))
        assertEquals("del", command.parameter("deleteKey"))
    }

    @Test
    fun semanticParameterHelpersAcceptCommonAliases() {
        val command = FutachaAiCommand(
            action = FutachaAiAction.DraftReply,
            parameters = mapOf(
                "boardName" to "二次元裏",
                "no" to "123",
                "sort" to "many",
                "keyword" to "検索語",
                "link" to "https://may.2chan.net/b/res/123.htm",
                "author" to "としあき",
                "mailAddress" to "sage",
                "topic" to "題名",
                "content" to "本文",
                "deletePassword" to "del"
            )
        )

        assertEquals("二次元裏", command.boardSelectorParameter())
        assertEquals("123", command.threadIdParameter())
        assertEquals("many", command.catalogModeParameter())
        assertEquals("検索語", command.searchQueryParameter())
        assertEquals("題名", command.titleParameter())
        assertEquals("https://may.2chan.net/b/res/123.htm", command.threadUrlParameter())
        assertEquals("https://may.2chan.net/b/res/123.htm", command.boardUrlParameter())
        assertEquals("としあき", command.draftNameParameter())
        assertEquals("sage", command.draftEmailParameter())
        assertEquals("題名", command.draftSubjectParameter())
        assertEquals("本文", command.draftCommentParameter())
        assertEquals("del", command.draftPasswordParameter())
    }

    @Test
    fun sanitizeCommandParametersRemovesUnsafeOrOversizedValues() {
        val sanitized = sanitizeFutachaAiCommandParameters(
            mapOf(
                " query\u0000 " to " 注\u0000目 ",
                "blank" to "   ",
                "comment" to "x".repeat(13_000)
            )
        )

        assertEquals("注目", sanitized["query"])
        assertNull(sanitized["blank"])
        assertEquals(12_000, sanitized.getValue("comment").length)
    }

    @Test
    fun parseDeepLinkSanitizesParameters() {
        val command = parseFutachaAiDeepLink(
            "futacha://ai?action=search_catalog&query=%00%E6%B3%A8%E7%9B%AE%00"
        )

        assertNotNull(command)
        assertEquals("注目", command.parameter("query"))
    }

    @Test
    fun parseDeepLinkToleratesActionKeyCaseAndSeparatorDifferences() {
        val actionCommand = parseFutachaAiDeepLink(
            "futacha://ai?Action=SaveCurrentThread&Thread-ID=12345"
        )
        val commandAlias = parseFutachaAiDeepLink(
            "futacha://ai?Command=open-board&Board=二次元裏"
        )

        assertNotNull(actionCommand)
        assertEquals(FutachaAiAction.SaveCurrentThread, actionCommand.action)
        assertNull(actionCommand.parameter("action"))
        assertEquals("12345", actionCommand.parameter("threadId"))

        assertNotNull(commandAlias)
        assertEquals(FutachaAiAction.OpenBoard, commandAlias.action)
        assertNull(commandAlias.parameter("command"))
        assertEquals("二次元裏", commandAlias.parameter("board"))
    }

    @Test
    fun parseDeepLinkSupportsPathAction() {
        val command = parseFutachaAiDeepLink(
            "futacha://ai/add_watch_word?word=%E3%83%86%E3%82%B9%E3%83%88"
        )

        assertNotNull(command)
        assertEquals(FutachaAiAction.AddWatchWord, command.action)
        assertEquals("テスト", command.parameter("word"))
    }

    @Test
    fun parseDeepLinkSupportsActionAliases() {
        val settings = parseFutachaAiDeepLink("futacha://ai/open%20settings")
        val save = parseFutachaAiDeepLink("futacha://ai?action=save&thread=123")

        assertNotNull(settings)
        assertEquals(FutachaAiAction.OpenGlobalSettings, settings.action)
        assertNotNull(save)
        assertEquals(FutachaAiAction.SaveCurrentThread, save.action)
        assertEquals("123", save.threadIdParameter())
    }

    @Test
    fun parseDeepLinkRejectsUnknownAction() {
        assertNull(parseFutachaAiDeepLink("futacha://ai?action=unknown"))
    }

    @Test
    fun parseDeepLinkRejectsNonFutachaAiScheme() {
        assertNull(parseFutachaAiDeepLink("https://example.com/?action=open_board&board=b"))
        assertNull(parseFutachaAiDeepLink("futacha://board?action=open_board&board=b"))
    }

    @Test
    fun buildDeepLinkCanRoundTripAsciiParameters() {
        val raw = buildFutachaAiDeepLink(
            action = FutachaAiAction.SetCatalogMode,
            parameters = mapOf("board" to "b", "mode" to "Many")
        )
        val parsed = parseFutachaAiDeepLink(raw)

        assertNotNull(parsed)
        assertEquals(FutachaAiAction.SetCatalogMode, parsed.action)
        assertEquals("b", parsed.parameter("board"))
        assertEquals("Many", parsed.parameter("mode"))
    }

    @Test
    fun buildDeepLinkCanRoundTripDraftParameters() {
        val raw = buildFutachaAiDeepLink(
            action = FutachaAiAction.DraftReply,
            parameters = mapOf(
                "board" to "b",
                "thread" to "12345",
                "name" to "としあき",
                "email" to "sage",
                "subject" to "返信",
                "comment" to "本文 + URL https://example.com/a?b=c",
                "password" to "del key"
            )
        )
        val parsed = parseFutachaAiDeepLink(raw)

        assertNotNull(parsed)
        assertEquals(FutachaAiAction.DraftReply, parsed.action)
        assertEquals("としあき", parsed.parameter("name"))
        assertEquals("sage", parsed.parameter("email"))
        assertEquals("返信", parsed.parameter("subject"))
        assertEquals("本文 + URL https://example.com/a?b=c", parsed.parameter("comment"))
        assertEquals("del key", parsed.parameter("password"))
    }
}
