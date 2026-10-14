package com.valoser.futacha.shared.ui.futaber

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.AI_UNTRUSTED_THREAD_URL_MESSAGE
import com.valoser.futacha.shared.ui.FutachaAppLockHolder
import com.valoser.futacha.shared.ui.compat.COMPAT_AI_COMMANDS_DISABLED_MESSAGE
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** B8 of the mode feature parity work: the AI summary / hiding (A06, A07) and the AI commands (X10-1, X10-2). */
class FutaberParityB8AiTest {
    private val may = BoardSummary("may-b", "may", "二次元裏", "https://may.2chan.net/b/futaba.php", "")
    private val img = BoardSummary("img-b", "img", "二次元裏", "https://img.2chan.net/b/futaba.php", "")
    private val tutorial = BoardSummary("t", "チュートリアル＠ふたちゃ", "チュートリアル", "https://www.example.com/t/futaba.php", "")
    private val boards = listOf(may, img, tutorial)
    private fun history(threadId: String, boardId: String, title: String = "スレ$threadId") = ThreadHistoryEntry(
        threadId = threadId, boardId = boardId, title = title, titleImageUrl = "", boardName = boardId,
        boardUrl = "https://may.2chan.net/b/res/$threadId.htm", lastVisitedEpochMillis = 1, replyCount = 10
    )
    private fun context(
        board: BoardSummary? = may,
        thread: FutaberThreadRef? = null,
        history: List<ThreadHistoryEntry> = emptyList()
    ) = FutaberAiContext(
        boards = boards, history = history, currentBoard = board, currentThread = thread,
        historyRefreshAvailable = true, cookiesAvailable = true, savedThreadsAvailable = true, appVersion = "12.8"
    )
    private fun command(action: FutachaAiAction, vararg parameters: Pair<String, String>, source: String = "siri") =
        FutachaAiCommand(action, parameters.toMap(), source)
    private fun post(id: String) = Post(id = id, author = null, subject = null, timestamp = "", messageHtml = "本文$id", imageUrl = null, thumbnailUrl = null)

    // ---- A06 / A07 ----

    @Test
    fun theModerationFollowsTheListByTheKeysOfItsRows() {
        val rows = listOf(FutaberRow(0, post("100")), FutaberRow(3, post("103")), FutaberRow(7, post("107")))
        assertEquals(listOf("100", "103", "107"), futaberAiOrderedIds(rows))
        // The thread list keys its rows "<ordinal>:<post id>" (the other modes use "<post>:<position>").
        assertEquals(mapOf("0:100" to "100", "3:103" to "103", "7:107" to "107"), futaberAiItemKeys(rows))
    }

    @Test
    fun aiHiddenPostsJoinTheNgOnesAndComeBackWithNgOffAndInTheNgExtract() {
        val ng = setOf("101")
        assertSame(ng, futaberHiddenWithAi(ng, emptySet(), emptySet()), "nothing added: the same set, nothing recomputed")
        val hidden = futaberHiddenWithAi(ng, setOf("102"), setOf("103"))
        assertEquals(setOf("101", "102", "103"), hidden)
        val posts = (100..104).map { post(it.toString()) }
        val shown = futaberVisibleRows(posts, FutaberViewFilter(), emptyMap(), hidden).map { it.post.id }
        assertEquals(listOf("100", "104"), shown)
        // "NGを無効にする" empties what is hidden: the AI's posts come back with the others.
        assertEquals(posts.map { it.id }, futaberVisibleRows(posts, FutaberViewFilter(), emptyMap(), emptySet(), ngHidden = hidden).map { it.post.id })
        // "NGで隠したレス" lists them, also while NG is off for the thread.
        val extracted = futaberVisibleRows(posts, FutaberViewFilter(extract = FutaberExtract.NgHidden), emptyMap(), emptySet(), ngHidden = hidden)
        assertEquals(listOf("101", "102", "103"), extracted.map { it.post.id })
    }

    @Test
    fun theSummaryPanelTextIsReadableInEveryTheme() {
        fun contrast(a: Color, b: Color): Float {
            val lighter = maxOf(a.luminance(), b.luminance())
            val darker = minOf(a.luminance(), b.luminance())
            return (lighter + 0.05f) / (darker + 0.05f)
        }
        // The panel draws on the Material surface (the bar) with onSurface (body) and onSurfaceVariant (meta).
        listOf(FutaberTokens.light, FutaberTokens.dark, FutaberTokens.oldDark, FutaberTokens.futaba).forEach { c ->
            assertTrue(contrast(c.body, c.bar) >= 4.5f)
            assertTrue(contrast(c.meta, c.bar) >= 4.5f, "meta on bar: ${contrast(c.meta, c.bar)}")
        }
    }

    // ---- X10-1: the plan of each command ----

    @Test
    fun withTheSwitchOffOnlyTheSettingsCommandsRun() {
        val off = futaberAiPlan(command(FutachaAiAction.OpenBoardList), context(), enabled = false)
        assertEquals(FutaberAiPlan.Fail(COMPAT_AI_COMMANDS_DISABLED_MESSAGE), off)
        assertTrue(FUTABER_AI_COMMANDS_DISABLED_MESSAGE.contains("AI・補助機能"))
        val settings = futaberAiPlan(command(FutachaAiAction.OpenGlobalSettings), context(), enabled = false)
        assertEquals(FutaberAiEffect.OpenSettings, assertIs<FutaberAiPlan.Run>(settings).effect)
    }

    @Test
    fun aCommandOutsideTheSubsetIsAnsweredAsNotSupported() {
        listOf(FutachaAiAction.SetCatalogMode, FutachaAiAction.OpenNgManagement, FutachaAiAction.OpenWatchWords, FutachaAiAction.OpenSavedThreads)
            .forEach { action ->
                val plan = assertIs<FutaberAiPlan.Fail>(futaberAiPlan(command(action), context(), enabled = true))
                assertTrue(plan.message.contains("ふたばー風モードでは使えません"), action.id)
            }
        // Every action is either run or answered: none is left for another mode to replay.
        FutachaAiAction.entries.forEach { action ->
            val plan = futaberAiPlan(command(action), context(thread = FutaberThreadRef(may.id, "123", "t", "", 1)), enabled = true)
            assertTrue(plan is FutaberAiPlan.Run || plan is FutaberAiPlan.Fail || plan is FutaberAiPlan.Confirm, action.id)
        }
    }

    @Test
    fun boardCommandsOpenTheNamedBoardOrAskToAddAnOfficialOne() {
        assertEquals(FutaberAiEffect.OpenDrawer, (futaberAiPlan(command(FutachaAiAction.OpenBoardList), context(), true) as FutaberAiPlan.Run).effect)
        val byName = futaberAiPlan(command(FutachaAiAction.OpenBoard, "board" to "img"), context(), true)
        assertEquals(FutaberAiEffect.OpenBoard(img), assertIs<FutaberAiPlan.Run>(byName).effect)
        val byUrl = futaberAiPlan(command(FutachaAiAction.OpenBoard, "url" to "https://img.2chan.net/b/"), context(), true)
        assertEquals(FutaberAiEffect.OpenBoard(img), assertIs<FutaberAiPlan.Run>(byUrl).effect)
        val unregistered = futaberAiPlan(command(FutachaAiAction.OpenBoard, "url" to "https://dat.2chan.net/b/futaba.php"), context(), true)
        val add = assertIs<FutaberAiEffect.AddBoardAndOpen>(assertIs<FutaberAiPlan.Run>(unregistered).effect)
        assertEquals("https://dat.2chan.net/b/", add.request.canonicalBoardUrl)
        val refresh = futaberAiPlan(command(FutachaAiAction.RefreshCatalog), context(board = img), true)
        assertEquals(FutaberAiEffect.RefreshCatalog(img), assertIs<FutaberAiPlan.Run>(refresh).effect)
        // A search word is not taken for a board name (ふたちゃ's selector also reads "query").
        val search = assertIs<FutaberAiPlan.Run>(futaberAiPlan(command(FutachaAiAction.SearchCatalog, "query" to "img"), context(board = may), true))
        val catalog = assertIs<FutaberAiEffect.CatalogCommand>(search.effect)
        assertEquals(may, catalog.board)
        assertTrue(futaberAiCommandIsForBoard(catalog.command, may.id))
        assertFalse(futaberAiCommandIsForBoard(catalog.command, img.id))
        assertEquals(FutaberAiEffect.OpenUrl(img.url), (futaberAiPlan(command(FutachaAiAction.OpenBoardExternally), context(board = img), true) as FutaberAiPlan.Run).effect)
    }

    @Test
    fun threadLinksAreFollowedOnlyOnFutabaOrARegisteredHost() {
        val open = futaberAiPlan(command(FutachaAiAction.OpenThreadFromUrl, "url" to "https://img.2chan.net/b/res/555.htm"), context(), true)
        val opened = assertIs<FutaberAiEffect.OpenThread>(assertIs<FutaberAiPlan.Run>(open).effect)
        assertEquals(img, opened.board)
        assertEquals("555", opened.ref.threadId)
        // A thread of an official board that is not registered asks to add the board (batch 1's question).
        val dat = futaberAiPlan(command(FutachaAiAction.OpenThread, "url" to "https://dat.2chan.net/b/res/9.htm"), context(), true)
        val add = assertIs<FutaberAiEffect.AddBoardAndOpen>(assertIs<FutaberAiPlan.Run>(dat).effect)
        assertEquals("9", add.request.threadId)
        val foreign = futaberAiPlan(command(FutachaAiAction.OpenThread, "url" to "https://evil.example/b/res/1.htm"), context(), true)
        assertEquals(FutaberAiPlan.Fail(AI_UNTRUSTED_THREAD_URL_MESSAGE), foreign)
        // The tutorial board's own host is a registered one.
        val tutorialThread = futaberAiPlan(command(FutachaAiAction.OpenThread, "url" to "https://www.example.com/t/res/1.htm"), context(), true)
        assertEquals(tutorial, assertIs<FutaberAiEffect.OpenThread>(assertIs<FutaberAiPlan.Run>(tutorialThread).effect).board)
        // By number: the history's row gives the board and the title.
        val byNumber = futaberAiPlan(command(FutachaAiAction.OpenThread, "thread" to "777"), context(board = may, history = listOf(history("777", img.id, "画像スレ"))), true)
        val ref = assertIs<FutaberAiEffect.OpenThread>(assertIs<FutaberAiPlan.Run>(byNumber).effect)
        assertEquals(img, ref.board)
        assertEquals("画像スレ", ref.ref.title)
    }

    @Test
    fun screenCommandsGoToTheThreadOnScreenOrTheOneTheyName() {
        assertEquals(
            FutaberAiPlan.Fail(FUTABER_AI_OPEN_THREAD_FIRST),
            futaberAiPlan(command(FutachaAiAction.ScrollThreadToBottom), context(), true)
        )
        val current = FutaberThreadRef(may.id, "123", "今のスレ", "", 5)
        val scroll = futaberAiPlan(command(FutachaAiAction.ScrollThreadToBottom), context(thread = current), true)
        val forwarded = assertIs<FutaberAiEffect.ThreadCommand>(assertIs<FutaberAiPlan.Run>(scroll).effect)
        assertSame(current, forwarded.ref)
        assertEquals("123", forwarded.command.parameter("threadId"))
        assertEquals(may.id, forwarded.command.parameter("boardId"))
        assertTrue(com.valoser.futacha.shared.ui.isAiCommandForThread(forwarded.command, may.id, "123"))
        assertFalse(com.valoser.futacha.shared.ui.isAiCommandForThread(forwarded.command, may.id, "124"))
        // A search word in "query" is not a board name; the thread on screen keeps the command.
        val search = futaberAiPlan(command(FutachaAiAction.SearchThread, "query" to "img"), context(thread = current), true)
        assertSame(current, assertIs<FutaberAiEffect.ThreadCommand>(assertIs<FutaberAiPlan.Run>(search).effect).ref)
        // Read-aloud and the others on a named thread: that thread is opened first.
        val named = futaberAiPlan(command(FutachaAiAction.StartThreadReadAloud, "thread" to "999", "board" to "img"), context(thread = current), true)
        val other = assertIs<FutaberAiEffect.ThreadCommand>(assertIs<FutaberAiPlan.Run>(named).effect)
        assertEquals(img, other.board)
        assertEquals("999", other.ref.threadId)
        val external = futaberAiPlan(command(FutachaAiAction.OpenThreadExternally), context(thread = current), true)
        assertEquals(FutaberAiEffect.OpenUrl("https://may.2chan.net/b/res/123.htm"), assertIs<FutaberAiPlan.Run>(external).effect)
    }

    @Test
    fun sharedSwitchesAndBasicNgAreChangedLikeTheOtherModes() {
        val on = futaberAiPlan(command(FutachaAiAction.EnableThreadSummaryMode), context(), true)
        assertEquals(FutaberAiEffect.SetFlag(FutaberAiFlag.ThreadSummary, true), assertIs<FutaberAiPlan.Run>(on).effect)
        val word = futaberAiPlan(command(FutachaAiAction.AddNgWord, "word" to " あいう "), context(), true)
        assertEquals(FutaberAiEffect.AddNgWord("あいう"), assertIs<FutaberAiPlan.Run>(word).effect)
        assertIs<FutaberAiPlan.Fail>(futaberAiPlan(command(FutachaAiAction.AddNgHeader), context(), true))
        // From a link, a change of a stored setting is asked first (S-2).
        val linked = futaberAiPlan(command(FutachaAiAction.AddNgWord, "word" to "x", source = "platform"), context(), true)
        assertIs<FutaberAiPlan.Confirm>(linked)
        assertEquals(listOf("a", "B"), listOf("a").futaberAppendDistinct("B").futaberAppendDistinct("b").futaberAppendDistinct(" "))
    }

    // ---- X10-2: asked first ----

    @Test
    fun commandsThatChangeDataAreAskedFirstWithTheWordingOfFutacha() {
        val current = FutaberThreadRef(may.id, "123", "今のスレ", "", 5)
        val save = assertIs<FutaberAiPlan.Confirm>(futaberAiPlan(command(FutachaAiAction.SaveCurrentThread), context(thread = current), true))
        assertTrue(save.message.startsWith("「現在のスレを保存」を実行します。"))
        assertTrue(save.message.contains("「123」"))
        val confirmed = assertIs<FutaberAiPlan.Run>(futaberAiPlan(save.command, context(thread = current), true, confirmed = true))
        assertEquals(FutachaAiAction.SaveCurrentThread, assertIs<FutaberAiEffect.ThreadCommand>(confirmed.effect).command.action)
        // "指定スレを保存" becomes the screen's save once that thread is open.
        val named = futaberAiPlan(command(FutachaAiAction.SaveThread, "thread" to "777", "board" to "img"), context(), true, confirmed = true)
        val namedSave = assertIs<FutaberAiEffect.ThreadCommand>(assertIs<FutaberAiPlan.Run>(named).effect)
        assertEquals(FutachaAiAction.SaveCurrentThread, namedSave.command.action)
        assertEquals("777", namedSave.ref.threadId)

        val delete = assertIs<FutaberAiPlan.Confirm>(futaberAiPlan(command(FutachaAiAction.DeleteBoard, "board" to "img"), context(), true))
        assertTrue(delete.message.contains("「img」"))
        val add = assertIs<FutaberAiPlan.Confirm>(futaberAiPlan(command(FutachaAiAction.AddBoard, "url" to "https://dat.2chan.net/b/futaba.php", "name" to "dat"), context(), true))
        assertTrue(add.message.contains("「dat」") && add.message.contains("dat.2chan.net"))
        assertIs<FutaberAiPlan.Fail>(futaberAiPlan(command(FutachaAiAction.AddBoard, "url" to "https://may.2chan.net/b/futaba.php"), context(), true))
        assertIs<FutaberAiPlan.Confirm>(futaberAiPlan(command(FutachaAiAction.ClearHistory), context(), true))
        val draft = assertIs<FutaberAiPlan.Run>(futaberAiPlan(command(FutachaAiAction.DraftThread, "subject" to "題", "comment" to "本文"), context(), true, confirmed = true))
        val draftEffect = assertIs<FutaberAiEffect.Draft>(draft.effect)
        assertEquals(FutaberPostTarget.CreateThread(may.id), draftEffect.target)
        assertEquals("題" to "本文", draftEffect.subject to draftEffect.comment)
        // A command that cannot be carried out is told at once, without a question.
        assertIs<FutaberAiPlan.Fail>(futaberAiPlan(command(FutachaAiAction.DeleteBoard, "board" to "nothing-like-it"), context(), true))
    }

    @Test
    fun theHistoryRowToDeleteMustBeTheOnlyOne() {
        val rows = listOf(history("1", may.id), history("1", img.id), history("2", may.id))
        assertEquals(FutaberAiHistoryMatch.Ambiguous, futaberAiHistoryEntry(command(FutachaAiAction.DeleteHistoryEntry, "thread" to "1"), context(history = rows)))
        assertEquals(
            FutaberAiHistoryMatch.Found(rows[1]),
            futaberAiHistoryEntry(command(FutachaAiAction.DeleteHistoryEntry, "thread" to "1", "board" to "img"), context(history = rows))
        )
        assertEquals(FutaberAiHistoryMatch.Missing, futaberAiHistoryEntry(command(FutachaAiAction.DeleteHistoryEntry, "thread" to "9"), context(history = rows)))
    }

    @Test
    fun aRepeatedCommandIdRunsOnce() {
        val handled = LinkedHashSet<String>()
        val first = command(FutachaAiAction.OpenBoardList, "commandId" to "abc")
        assertFalse(futaberIsDuplicateAiCommand(handled, first))
        assertTrue(futaberIsDuplicateAiCommand(handled, first))
        assertFalse(futaberIsDuplicateAiCommand(handled, command(FutachaAiAction.OpenBoardList)), "without an id every delivery runs")
        assertFalse(futaberIsDuplicateAiCommand(handled, command(FutachaAiAction.OpenBoardList)))
    }

    // ---- the lock and the wait for the screen ----

    private fun unlockedLock() = FutachaAppLockHolder().apply { openSessionWithoutLock(); setContentVisible(true) }

    @Test
    fun aDeliveredCommandRunsOnTheScreen() = runBlocking<Unit> {
        val inbox = FutaberAiInbox()
        val ran = mutableListOf<FutachaAiAction>()
        inbox.runner = { ran += it.action }
        val notices = mutableListOf<String>()
        inbox.deliver(command(FutachaAiAction.OpenBoardList), TimeSource.Monotonic.markNow(), null, unlockedLock()) { notices += it }
        assertEquals(listOf(FutachaAiAction.OpenBoardList), ran)
        assertTrue(notices.isEmpty())
    }

    @Test
    fun aCommandHeldBehindTheLockTooLongOrTooOldIsNotRun() = runBlocking<Unit> {
        val inbox = FutaberAiInbox()
        val ran = mutableListOf<FutachaAiAction>()
        inbox.runner = { ran += it.action }
        val notices = mutableListOf<String>()
        val lock = unlockedLock()
        val arrived = TimeSource.Monotonic.markNow() - 61.seconds
        lock.unlockSession() // the password was entered after the command arrived, more than a minute ago
        inbox.deliver(command(FutachaAiAction.OpenBoardList), arrived, null, lock) { notices += it }
        assertTrue(ran.isEmpty())
        assertTrue(notices.single().contains("起動ロック中"))
        // Older than its sender allows (a watch tap): dropped without a word.
        inbox.deliver(command(FutachaAiAction.OpenBoardList), TimeSource.Monotonic.markNow() - 5.seconds, 1_000L, unlockedLock()) { notices += it }
        assertTrue(ran.isEmpty())
        assertEquals(1, notices.size)
    }

    @Test
    fun withoutTheScreenTheCommandIsDroppedWithAWord() = runBlocking<Unit> {
        val inbox = FutaberAiInbox()
        val notices = mutableListOf<String>()
        inbox.deliver(command(FutachaAiAction.OpenBoardList), TimeSource.Monotonic.markNow(), null, unlockedLock(), readyTimeoutMillis = 10) { notices += it }
        assertTrue(notices.single().contains("準備が終わらなかった"))
        assertNull(inbox.runner)
    }
}
