package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.modernBoardsToCompatibility
import com.valoser.futacha.shared.compat.synchronizeModernBoardsFromCompatibility
import com.valoser.futacha.shared.model.BoardSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CompatBoardPinSupportTest {
    private val mayUrl = "https://may.2chan.net/b/"
    private val imgUrl = "https://img.2chan.net/b/"
    private fun summary(id: String, url: String, pinned: Boolean = false) = BoardSummary(id, id, "cat", url, "d", pinned)

    @Test
    fun pinnedKeysMapAppStateBoardsOntoTheCompatibilityBoardKeys() {
        val boards = listOf(
            summary("may", "https://may.2chan.net/b/futaba.php", pinned = true),
            summary("img", imgUrl),
            summary("custom", "https://example.org/custom", pinned = true), // not a Futaba board: no compat key
            summary("tutorial", "https://www.example.com/t/futaba.php", pinned = true)
        )
        assertEquals(setOf(compatBoardKey(mayUrl)), compatPinnedBoardKeys(boards))
        assertEquals(emptySet(), compatPinnedBoardKeys(emptyList()))
    }

    @Test
    fun toggleFlipsOnlyTheBoardTheKeyStandsFor() {
        val boards = listOf(summary("may", "https://may.2chan.net/b/futaba.php"), summary("img", imgUrl, pinned = true))
        val pinned = toggleCompatBoardPinned(boards, compatBoardKey(mayUrl))
        assertEquals(listOf(true, true), pinned.map { it.pinned })
        val unpinned = toggleCompatBoardPinned(pinned, compatBoardKey(imgUrl))
        assertEquals(listOf(true, false), unpinned.map { it.pinned })
        // The order and everything else are untouched: the pin only marks the board.
        assertEquals(boards.map { it.id }, pinned.map { it.id })
        assertEquals(boards[0].copy(pinned = true), pinned[0])
    }

    @Test
    fun toggleIsANoOpForAnUnknownKey() {
        val boards = listOf(summary("may", mayUrl))
        assertSame(boards, toggleCompatBoardPinned(boards, compatBoardKey("https://nov.2chan.net/b/")))
    }

    @Test
    fun theLongPressMenuAddsThePinEntryAtTheEndOnlyWhereItCanBeSaved() {
        assertEquals(listOf("名前を変更", "削除する"), compatBoardContextChoices(pinned = false, pinAvailable = false))
        assertEquals(listOf("名前を変更", "削除する"), compatBoardContextChoices(pinned = true, pinAvailable = false))
        assertEquals(listOf("名前を変更", "削除する", "ピン留め"), compatBoardContextChoices(pinned = false, pinAvailable = true))
        assertEquals(listOf("名前を変更", "削除する", "ピン留めを解除"), compatBoardContextChoices(pinned = true, pinAvailable = true))
    }

    @Test
    fun aPinSurvivesRenamingTheBoardInToshiakiAndKeepsItsPlace() {
        val modern = listOf(summary("may", mayUrl, pinned = true), summary("img", imgUrl))
        // としあき(仮) renames the pinned board and syncs: the pin (kept only in the AppState) must remain.
        val renamed = modernBoardsToCompatibility(modern).map { if (it.canonicalUrl == mayUrl) it.copy(name = "新しい名前") else it }
        val synchronized = synchronizeModernBoardsFromCompatibility(modern, renamed)
        assertEquals(listOf("may", "img"), synchronized.map { it.id })
        assertEquals("新しい名前", synchronized[0].name)
        assertTrue(synchronized[0].pinned)
        assertFalse(synchronized[1].pinned)
        assertEquals(setOf(compatBoardKey(mayUrl)), compatPinnedBoardKeys(synchronized))
    }

    @Test
    fun aPinSurvivesReorderingAndAddingBoardsInToshiaki() {
        val modern = listOf(summary("may", mayUrl, pinned = true), summary("img", imgUrl))
        val added = CompatBoard(compatBoardKey("https://nov.2chan.net/b/"), "nov", "https://nov.2chan.net/b/", "https://nov.2chan.net/b/", 0)
        val reordered = modernBoardsToCompatibility(modern).reversed().mapIndexed { i, b -> b.copy(sortOrder = i + 1) } + added
        val synchronized = synchronizeModernBoardsFromCompatibility(modern, reordered)
        assertEquals(setOf(compatBoardKey(mayUrl)), compatPinnedBoardKeys(synchronized))
        assertEquals(3, synchronized.size)
    }

    @Test
    fun deletingAndAddingTheBoardAgainStartsUnpinned() {
        val modern = listOf(summary("may", mayUrl, pinned = true), summary("img", imgUrl))
        val afterDelete = synchronizeModernBoardsFromCompatibility(modern, modernBoardsToCompatibility(modern).filter { it.canonicalUrl != mayUrl })
        assertEquals(listOf("img"), afterDelete.map { it.id })
        val afterReAdd = synchronizeModernBoardsFromCompatibility(
            afterDelete,
            modernBoardsToCompatibility(modern).mapIndexed { i, b -> b.copy(sortOrder = i) }
        )
        assertTrue(afterReAdd.none { it.pinned })
    }
}
