package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatSettingsBackup
import com.valoser.futacha.shared.compat.CompatSharedTabView
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.compat.decodeCompatSettingsBackup
import com.valoser.futacha.shared.compat.encodeCompatSettingsBackup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** AM-B5: the あいもげ part of a settings backup goes in `siteBoards`; the Futaba part is unchanged. */
class AimogeSiteBoardsBackupTest {
    private val fUrl = "https://may.2chan.net/b/"
    private val aUrl = "https://nijiurachan.net/b/aimoge/"
    private val fBoard = CompatBoard(compatBoardKey(fUrl), "may", fUrl, "${fUrl}futaba.php", 0)
    private val aBoard = CompatBoard(compatBoardKey(aUrl), "あいもげ", aUrl, aUrl, 1)
    private val aThread = "${aUrl}thread/0193b2c4-1a2b-7c3d-8e4f-5a6b7c8d9e0f"
    private val fThread = "${fUrl}res/123.htm"
    private fun tab(url: String, board: CompatBoard) = CompatTab(
        key = compatTabKey(url), canonicalUrl = url, originalUrl = url, boardKey = board.key, boardName = board.name,
        threadNo = "1", title = "t", insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L
    )
    private fun history(url: String, board: CompatBoard) = CompatHistoryEntry(
        canonicalUrl = url, originalUrl = url, boardKey = board.key, boardName = board.name, threadNo = "1", title = "t",
        contentUpdatedAtEpochMillis = 1L
    )
    private fun rule(id: String, scope: String) = CompatNgRule(id, CompatNgKind.CATALOG_WORD, scope, "w", 1L)
    private val futabaOnly = CompatSettingsBackup(
        exportedAtEpochMillis = 1L, boards = listOf(fBoard), tabs = listOf(tab(fThread, fBoard)),
        history = listOf(history(fThread, fBoard)), ngRules = listOf(rule("f", fBoard.key), rule("all", "*")),
        tabViews = listOf(CompatSharedTabView(compatTabKey(fThread), "futacha", 0, 1L))
    )
    private val mixed = futabaOnly.copy(
        boards = futabaOnly.boards + aBoard,
        tabs = futabaOnly.tabs + tab(aThread, aBoard),
        history = futabaOnly.history + history(aThread, aBoard),
        ngRules = futabaOnly.ngRules + rule("a", aBoard.key) + rule("at", compatTabKey(aThread)),
        tabViews = futabaOnly.tabViews + CompatSharedTabView(compatTabKey(aThread), "futacha", 1, 1L)
    )

    @Test
    fun aFutabaOnlyFileHasNoSiteBoardsField() {
        assertFalse("siteBoards" in encodeCompatSettingsBackup(futabaOnly))
    }

    @Test
    fun theAimogePartIsKeptApartAndComesBackJoined() {
        val encoded = encodeCompatSettingsBackup(mixed)
        val root = Json.parseToJsonElement(encoded).jsonObject
        assertEquals(1, root.getValue("boards").jsonArray.size)
        assertEquals(2, root.getValue("ngRules").jsonArray.size)
        val site = root.getValue("siteBoards").jsonObject
        assertEquals(1, site.getValue("boards").jsonArray.size)
        assertEquals(2, site.getValue("ngRules").jsonArray.size)
        val decoded = decodeCompatSettingsBackup(encoded)
        assertEquals(mixed.boards.toSet(), decoded.boards.toSet())
        assertEquals(mixed.tabs.toSet(), decoded.tabs.toSet())
        assertEquals(mixed.history.toSet(), decoded.history.toSet())
        assertEquals(mixed.ngRules.toSet(), decoded.ngRules.toSet())
        assertEquals(mixed.tabViews.toSet(), decoded.tabViews.toSet())
    }

    @Test
    fun theFutabaPartOfAMixedFileIsWhatAFutabaOnlyFileWouldBe() {
        // An older version skips siteBoards: what it reads equals the Futaba-only file.
        val lenient = Json { ignoreUnknownKeys = true }
        val olderReadsMixed = lenient.parseToJsonElement(encodeCompatSettingsBackup(mixed)).jsonObject - "siteBoards"
        val olderReadsFutaba = lenient.parseToJsonElement(encodeCompatSettingsBackup(futabaOnly)).jsonObject
        assertEquals(olderReadsFutaba, olderReadsMixed)
    }

    @Test
    fun theLimitsAndDuplicatesSeeBothParts() {
        val duplicated = mixed.copy(siteBoards = com.valoser.futacha.shared.compat.CompatSiteBoardsBackup(boards = listOf(aBoard)))
        assertFailsWith<IllegalArgumentException> { encodeCompatSettingsBackup(duplicated) }
        val tooMany = mixed.copy(history = (0 until 150).map { history("${fUrl}res/$it.htm", fBoard) },
            siteBoards = com.valoser.futacha.shared.compat.CompatSiteBoardsBackup(
                boards = emptyList(), history = (0 until 60).map { history("${aUrl}thread/0193b2c4-1a2b-7c3d-8e4f-${it.toString().padStart(12, '0')}", aBoard) }))
        val error = assertFailsWith<IllegalArgumentException> { encodeCompatSettingsBackup(tooMany) }
        assertTrue(error.message.orEmpty().contains("履歴"))
    }
}
