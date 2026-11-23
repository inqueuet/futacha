package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.repo.aimoge.AimogeBoardInfo
import com.valoser.futacha.shared.ui.aimoge.AIMOGE_BOARD_URL_DISABLED_MESSAGE
import com.valoser.futacha.shared.ui.aimoge.AimogeBoardLookup
import com.valoser.futacha.shared.ui.aimoge.AimogeBoardUrlInput
import com.valoser.futacha.shared.ui.aimoge.aimogeBoardUrlOfInput
import com.valoser.futacha.shared.ui.aimoge.classifyAimogeBoardUrlInput
import com.valoser.futacha.shared.ui.aimoge.lookupAimogeBoard
import com.valoser.futacha.shared.ui.board.buildAddBoardValidationState
import com.valoser.futacha.shared.ui.board.createCustomBoardSummary
import com.valoser.futacha.shared.ui.board.withAimoge
import com.valoser.futacha.shared.ui.compat.CompatAimogeBoardInput
import com.valoser.futacha.shared.ui.compat.compatAimogeBoardInput
import com.valoser.futacha.shared.ui.compat.compatMainOverflowLabels
import com.valoser.futacha.shared.ui.ftutter.FTUTTER_AIMOGE_SECTION
import com.valoser.futacha.shared.ui.ftutter.FtutterBoardInput
import com.valoser.futacha.shared.ui.ftutter.classifyFtutterBoardInput
import com.valoser.futacha.shared.ui.ftutter.fetchFtutterAimogeBoardName
import com.valoser.futacha.shared.ui.ftutter.ftutterBoardSection
import com.valoser.futacha.shared.ui.futaber.FutaberDrawerEditExtra
import com.valoser.futacha.shared.ui.futaber.futaberAddBoardAlertReason
import com.valoser.futacha.shared.ui.futaber.futaberBoardDrawerEditExtras
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** あいもげの板は、4モードの「板を追加」の URL 欄から足す（一覧のダイアログは無くなった）。 */
class AimogeAddBoardEntriesTest {
    private val aUrl = "https://nijiurachan.net/b/aimg/"
    private val info = AimogeBoardInfo("aimg", "二次元裏", aUrl, r18 = true)
    private val may = BoardSummary("may-b", "may", "", "https://may.2chan.net/b/futaba.php", "")
    private val registered = BoardSummary("aimoge-aimg", "二次元裏", "", aUrl, "")

    @Test
    fun theUrlShapesAllBecomeTheCanonicalBoard() {
        listOf(
            "https://nijiurachan.net/b/aimg/",
            "https://nijiurachan.net/b/aimg",
            "http://nijiurachan.net/b/aimg/",
            " HTTPS://NIJIURACHAN.NET/b/aimg ",
            "https://nijiurachan.net/b/aimg/catalog",
            "https://nijiurachan.net/b/aimg/catalog/?sort=new",
            "https://nijiurachan.net/b/aimg/thread/0f8fad5b-d9cb-469f-a165-70867728950e",
            "https://nijiurachan.net/b/aimg/thread/0f8fad5b-d9cb-469f-a165-70867728950e#post-0f8fad5b-d9cb-469f-a165-70867728950e",
            "https://nijiurachan.net/b/aimg/?x=1"
        ).forEach { assertEquals(aUrl, aimogeBoardUrlOfInput(it), it) }
        listOf(
            "", "https://nijiurachan.net/", "https://nijiurachan.net/b/", "https://api.nijiurachan.net/b/aimg/",
            "https://evil.example/b/aimg/", "https://may.2chan.net/b/futaba.php", "https://nijiurachan.net/b/aimg/app-auth"
        ).forEach { assertNull(aimogeBoardUrlOfInput(it), it) }
    }

    @Test
    fun whileOffEveryModeRefusesWithTheReason() = withAimoge(enabled = false) {
        assertEquals(AimogeBoardUrlInput.Refused(AIMOGE_BOARD_URL_DISABLED_MESSAGE), classifyAimogeBoardUrlInput(aUrl))
        assertEquals(AimogeBoardUrlInput.NotAimoge, classifyAimogeBoardUrlInput(may.url))
        // ふたちゃ
        val futacha = buildAddBoardValidationState("", aUrl, emptyList()).withAimoge(classifyAimogeBoardUrlInput(aUrl), emptyList())
        assertFalse(futacha.canSubmit)
        assertEquals(AIMOGE_BOARD_URL_DISABLED_MESSAGE, futacha.helperText)
        // としあき(仮)
        assertEquals(CompatAimogeBoardInput.Refused(AIMOGE_BOARD_URL_DISABLED_MESSAGE), compatAimogeBoardInput(aUrl, emptyList()))
        // ふたばー風
        assertEquals(AIMOGE_BOARD_URL_DISABLED_MESSAGE, futaberAddBoardAlertReason(aUrl, emptyList()))
        // ふたったー風
        assertEquals(FtutterBoardInput.Invalid(AIMOGE_BOARD_URL_DISABLED_MESSAGE), classifyFtutterBoardInput(aUrl, emptyList()))
        Unit
    }

    @Test
    fun whileOnEveryModeTakesTheUrlAndRefusesADuplicate() = withAimoge(enabled = true) {
        val typed = "http://nijiurachan.net/b/aimg/catalog"
        // ふたちゃ: the name may be empty, the URL becomes canonical.
        val futacha = buildAddBoardValidationState("", typed, listOf(may)).withAimoge(classifyAimogeBoardUrlInput(typed), listOf(may))
        assertTrue(futacha.canSubmit)
        assertEquals(aUrl, futacha.normalizedInputUrl)
        val dup = buildAddBoardValidationState("", typed, listOf(registered)).withAimoge(classifyAimogeBoardUrlInput(typed), listOf(registered))
        assertFalse(dup.canSubmit)
        assertEquals("同じURLの板が既に登録されています", dup.helperText)
        // としあき(仮)
        assertEquals(CompatAimogeBoardInput.Add(aUrl), compatAimogeBoardInput(typed, emptyList()))
        val compatRegistered = CompatBoard(compatBoardKey(aUrl), "二次元裏", aUrl, aUrl, 0)
        assertEquals(CompatAimogeBoardInput.Refused("既に登録されています"), compatAimogeBoardInput(typed, listOf(compatRegistered)))
        // ふたばー風
        assertNull(futaberAddBoardAlertReason(typed, listOf(may)))
        assertEquals("同じURLの板が既に登録されています", futaberAddBoardAlertReason(typed, listOf(registered)))
        // ふたったー風
        assertEquals(FtutterBoardInput.OneBoard(aUrl), classifyFtutterBoardInput(typed, emptyList()))
        assertEquals(FtutterBoardInput.Invalid("この板は登録済みです"), classifyFtutterBoardInput(typed, listOf(registered)))
        Unit
    }

    @Test
    fun theListEntriesAreGone() {
        assertEquals(listOf("板一覧", "新規追加", "URLから開く", "並び替え", "削除", "保存済みスレッド", "設定", "ヘルプ"), compatMainOverflowLabels())
        assertEquals(listOf(FutaberDrawerEditExtra.BulkAdd, FutaberDrawerEditExtra.Rename), futaberBoardDrawerEditExtras(true, true))
    }

    @Test
    fun theNameComesFromTheListOrTheSlug() = runTest {
        assertEquals(AimogeBoardLookup(aUrl, "二次元裏", r18 = true), lookupAimogeBoard(aUrl, { listOf(info) }))
        assertEquals(AimogeBoardLookup(aUrl, "aimg", r18 = false), lookupAimogeBoard(aUrl, { error("offline") }))
        assertEquals(AimogeBoardLookup(aUrl, "aimg", r18 = false), lookupAimogeBoard(aUrl, { emptyList() }))
    }

    @Test
    fun anAddedBoardHasTheCanonicalAddressAndTheAimogeId() = withAimoge(enabled = true) {
        val added = createCustomBoardSummary(info.name, info.url, listOf(may))
        assertEquals("aimoge-aimg", added.id)
        assertEquals(aUrl, added.url)
        val futaba = createCustomBoardSummary("img", "https://img.2chan.net/b/futaba.php", listOf(may))
        assertFalse(futaba.id.startsWith("aimoge-"))
    }

    @Test
    fun ftutterNamesTheBoardFromTheListAndShowsItsOwnSection() = runTest {
        assertEquals("二次元裏", fetchFtutterAimogeBoardName(aUrl) { listOf(info) })
        assertNull(fetchFtutterAimogeBoardName("https://nijiurachan.net/b/none/") { listOf(info) })
        assertEquals("aimg", fetchFtutterAimogeBoardName(aUrl) { error("offline") })
        withAimoge(enabled = true) {
            assertEquals(FTUTTER_AIMOGE_SECTION, ftutterBoardSection(BoardSummary("aimoge-aimg", "x", "", aUrl, "")))
        }
        assertEquals("2chan.net", ftutterBoardSection(may))
    }
}
