package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.ScrollAnchor
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** B7b: the shared picture list's "元レスに移動する" goes to the post in this mode and never writes another mode's tab. */
class FutaberGalleryStoreTest {
    @Test fun goingToThePostDoesNotWriteTheOtherModesTab() = runBlocking {
        val root = Files.createTempDirectory("futaber-gallery-store").toFile()
        val desktop = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            desktop.initialize()
            val board = "https://may.2chan.net/b/"
            val url = "${board}res/123.htm"
            desktop.importModernBoards(listOf(BoardSummary("may-b", "may", "", "${board}futaba.php", "")))
            // としあき(仮) has a tab of the same thread (the key the ふたばー風 gallery uses too).
            desktop.openTab(CompatTab(compatTabKey(url), url, url, compatBoardKey(board), "may", "123", "スレ",
                insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L))
            val shown = mutableListOf<String>()
            val store = FutaberGalleryStore(desktop) { shown += it }
            store.updateScrollAnchor(compatTabKey(url), ScrollAnchor(postNo = "130", fallbackIndex = 7))
            store.updateScrollAnchor(compatTabKey(url), ScrollAnchor(postNo = null))
            assertEquals(listOf("130"), shown)
            assertEquals(ScrollAnchor(), desktop.tabs.first().single().scrollAnchor)
            // Everything else is the shared store itself.
            assertEquals(desktop.tabs.first(), store.tabs.first())
        } finally {
            root.deleteRecursively()
        }
    }
}
