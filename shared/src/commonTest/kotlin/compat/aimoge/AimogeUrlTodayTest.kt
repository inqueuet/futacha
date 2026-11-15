package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatInlineUrlRoute
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatibilityBoardSynchronizationKeys
import com.valoser.futacha.shared.compat.decodeCompatLegacyBackup
import com.valoser.futacha.shared.compat.modernBoardsToCompatibility
import com.valoser.futacha.shared.compat.resolveCompatInlineUrlRoute
import com.valoser.futacha.shared.compat.shared.SharedBoardSide
import com.valoser.futacha.shared.compat.shared.SharedBoardsRecord
import com.valoser.futacha.shared.compat.shared.planSharedBoards
import com.valoser.futacha.shared.compat.shared.sharedBoardKey
import com.valoser.futacha.shared.compat.synchronizeModernBoardsFromCompatibility
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.compat.parseCompatBoardMenu
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtLibrary
import com.valoser.futacha.shared.ui.resolveFutachaAddableBoardUrl
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AM-B0: what the places that read "an official Futaba board" from `canonicalizeBoardUrl` / `canonicalizeThreadUrl` do
 * with an あいもげ address today. AM-B1 moves only some of them to the new site functions; the rest must keep these
 * results after it.
 */
class AimogeUrlTodayTest {
    private val aimogeBoard = "https://nijiurachan.net/b/aimoge/"
    private val aimogeThread = "https://nijiurachan.net/b/aimoge/thread/0193b2c4-1a2b-7c3d-8e4f-5a6b7c8d9e0f"
    private val may = BoardSummary(id = "may-b", name = "may/b", category = "", url = "https://may.2chan.net/b/futaba.php", description = "")
    private val aimoge = BoardSummary(id = "aimoge", name = "あいもげ", category = "", url = aimogeBoard, description = "")

    @Test
    fun theSharedBoardSynchronizationLeavesAnAimogeBoardOutOfTheCompatibilityStore() {
        assertNull(sharedBoardKey(aimogeBoard, compatHoldsNonOfficial = false))
        assertNull(sharedBoardKey(aimogeBoard, compatHoldsNonOfficial = true))
        assertEquals(listOf("https://may.2chan.net/b/"), modernBoardsToCompatibility(listOf(may, aimoge)).map(CompatBoard::canonicalUrl))
        assertEquals(setOf(compatBoardKey("https://may.2chan.net/b/")), compatibilityBoardSynchronizationKeys(listOf(may, aimoge)))
        for (holds in listOf(false, true)) {
            val plan = planSharedBoards(
                side = SharedBoardSide.APP_STATE,
                appState = listOf(may, aimoge),
                compat = emptyList(),
                record = SharedBoardsRecord(),
                compatHoldsNonOfficial = holds,
                nowEpochMillis = 1_000L
            )
            assertEquals(listOf("https://may.2chan.net/b/"), plan.compatUpserts.map(CompatBoard::canonicalUrl), "holds=$holds")
            assertTrue(plan.removeFromAppState.isEmpty(), "holds=$holds")
            // The iOS/desktop store imports a non-official AppState board by its address: an あいもげ board is not one it
            // can key (sharedBoardKey is null), so it is not imported either.
            assertTrue(plan.compatImports.none { it.url == aimogeBoard }, "holds=$holds")
        }
    }

    @Test
    fun synchronizingFromTheCompatibilityStoreKeepsTheAimogeBoardWhereItWas() {
        val compat = modernBoardsToCompatibility(listOf(may))
        val result = synchronizeModernBoardsFromCompatibility(listOf(aimoge, may), compat, compatibilityHoldsTutorial = true)
        assertEquals(listOf(aimogeBoard, "https://may.2chan.net/b/futaba.php"), result.map(BoardSummary::url))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun anOldApkBackupDropsAnAimogeBoardAndItsScopedWords() {
        val json = """
            {"strFileType":"setting",
             "arrBoardList":[["may%2Fb","http%3A%2F%2Fmay.2chan.net%2Fb%2F","999999","1","201"],
                             ["aimoge","https%3A%2F%2Fnijiurachan.net%2Fb%2Faimoge%2F","999999","1","201"]]}
        """.trimIndent()
        val boards = decodeCompatLegacyBackup(Base64.encode(json.encodeToByteArray())).boards
        assertEquals(listOf("https://may.2chan.net/b/"), boards.map { it.canonicalUrl })

        val keyword = """
            {"strFileType":"keyword",
             "arrCatalogExtractList":[["https%3A%2F%2Fnijiurachan.net%2Fb%2Faimoge%2F","word"]],
             "arrCatalogIgnoreList":[],"arrThreadRefuseList":[],"arrThreadRefuseOnlyList":[],
             "arrThreadIgnoreList":[],"arrThreadIgnoreOnlyList":[]}
        """.trimIndent()
        val words = decodeCompatLegacyBackup(Base64.encode(keyword.encodeToByteArray())).catalogWatchWords
        assertEquals(listOf<String?>(null), words.map { it.boardUrl })
    }

    @Test
    fun anMhtPictureOnAimogeIsNotAnOfficialOne() {
        assertTrue(FutaberMhtLibrary.isOfficialMediaUrl("https://may.2chan.net/b/src/1.jpg"))
        assertEquals(false, FutaberMhtLibrary.isOfficialMediaUrl("https://nijiurachan.net/b/aimoge/src/1.jpg"))
        assertEquals(false, FutaberMhtLibrary.isOfficialMediaUrl("https://api.nijiurachan.net/media/1.jpg"))
    }

    @Test
    fun theBoardMenuUpdateSkipsAnAimogeLink() {
        val html = """
            <a href="//may.2chan.net/b/futaba.php" target="cont">二次元裏</a>
            <a href="https://nijiurachan.net/b/aimoge/futaba.php" target="cont">あいもげ</a>
        """.trimIndent()
        val urls = parseCompatBoardMenu(html, emptyList()).map(CompatBoard::canonicalUrl)
        assertTrue("https://may.2chan.net/b/" in urls)
        assertTrue(urls.none { it.contains("nijiurachan") }, urls.toString())
    }

    @Test
    fun anAimogeHistoryRowOffersNoBoardToAdd() {
        fun row(url: String) = ThreadHistoryEntry(
            threadId = "1", title = "t", titleImageUrl = "", boardName = "b", boardUrl = url,
            lastVisitedEpochMillis = 0L, replyCount = 0
        )
        assertEquals("https://may.2chan.net/b/", resolveFutachaAddableBoardUrl(row("https://may.2chan.net/b/res/1.htm")))
        assertNull(resolveFutachaAddableBoardUrl(row(aimogeThread)))
        assertNull(resolveFutachaAddableBoardUrl(row(aimogeBoard)))
    }

    @Test
    fun anAimogeLinkInPostTextOpensOutside() {
        val registered = mapOf("https://may.2chan.net/b/" to "k")
        assertTrue(resolveCompatInlineUrlRoute("https://may.2chan.net/b/res/1.htm", registered) is CompatInlineUrlRoute.RegisteredThread)
        assertEquals(CompatInlineUrlRoute.External, resolveCompatInlineUrlRoute(aimogeThread, registered + (aimogeBoard to "a")))
        assertEquals(CompatInlineUrlRoute.External, resolveCompatInlineUrlRoute(aimogeBoard, registered))
    }
}
