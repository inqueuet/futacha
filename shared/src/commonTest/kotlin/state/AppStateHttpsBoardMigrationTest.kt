package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.BoardSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * L100 (the decision): a 2chan board saved with `http://` could not post on iOS (no http) nor through the official
 * pre-submit step, so the stored ones are moved to https at start. Boards of other hosts keep their address.
 */
class AppStateHttpsBoardMigrationTest {
    private fun board(id: String, url: String) = BoardSummary(id, id, "", url, "")

    @Test
    fun stored2chanHttpBoardsMoveToHttpsAndOthersStay() = runBlocking {
        val storage = FakePlatformStateStorage()
        val store = AppStateStore(storage)
        store.setBoards(listOf(
            board("b", "http://may.2chan.net/b/futaba.php"),
            board("img", "HTTP://img.2chan.net/b/futaba.php?mode=cat"),
            board("jun", "https://jun.2chan.net/jun/futaba.php"),
            board("other", "http://www.example.org/b/futaba.php"),
            board("look-alike", "http://may.2chan.net.example.org/b/futaba.php")
        ))

        store.seedIfEmpty(AppStateSeedDefaults(boards = emptyList(), history = emptyList()))

        assertEquals(
            listOf(
                "https://may.2chan.net/b/futaba.php",
                "https://img.2chan.net/b/futaba.php?mode=cat",
                "https://jun.2chan.net/jun/futaba.php",
                "http://www.example.org/b/futaba.php",
                "http://may.2chan.net.example.org/b/futaba.php"
            ),
            store.boards.first().map { it.url }
        )
        // Ids, names and the order are kept; a second start finds nothing more to move.
        assertEquals(listOf("b", "img", "jun", "other", "look-alike"), store.boards.first().map { it.id })
        val migrated = storage.boardsJson.first()
        store.seedIfEmpty(AppStateSeedDefaults(boards = emptyList(), history = emptyList()))
        assertEquals(migrated, storage.boardsJson.first())
    }
}
