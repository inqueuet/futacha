package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Audit round 5, N1: a first opening writes the tab before the history row, so the screen hands over what it knows. */
class FtutterTabSeedTest {
    private val may = BoardSummary("may-b", "二次元裏", "", "https://may.2chan.net/b/futaba.php", "")
    private val url = "https://may.2chan.net/b/res/99.htm"

    private fun withStore(block: suspend (DesktopCompatibilityStore) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("ftutter-tab-seed").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            store.importModernBoards(listOf(may))
            block(store)
        } finally {
            store.close()
            root.deleteRecursively()
        }
    }

    private fun writer(store: DesktopCompatibilityStore) =
        FtutterStoreTabWriter(store, { listOf(may) }, { emptyList() }, { _, _ -> }, {}, { 1_000L })

    @Test fun aFirstOpeningKeepsTheTitleThePictureAndTheReplies() = withStore { store ->
        writer(store).open(url, FtutterTabSeed("スレの題名", "https://may.2chan.net/b/cat/99s.jpg", 7))
        val tab = store.tabs.first().single()
        assertEquals("スレの題名", tab.title)
        assertEquals("https://may.2chan.net/b/cat/99s.jpg", tab.thumbnailUrl)
        assertEquals(7, tab.replyCount)
    }

    @Test fun anOpeningWithNothingKnownStaysUntitled() = withStore { store ->
        writer(store).open(url, FtutterTabSeed("", "", 0))
        val tab = store.tabs.first().single()
        assertEquals("(無題)", tab.title)
        assertNull(tab.thumbnailUrl)
    }

    @Test fun aLaterOpeningNamesATabAlreadyUntitled() = withStore { store ->
        writer(store).open(url)
        assertEquals("(無題)", store.tabs.first().single().title)
        writer(store).open(url, FtutterTabSeed("あとから分かった題名", "", 3))
        assertEquals("あとから分かった題名", store.tabs.first().single().title)
    }
}
