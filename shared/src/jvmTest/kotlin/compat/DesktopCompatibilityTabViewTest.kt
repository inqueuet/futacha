package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** D-B4: one mode's mark and order on the shared tabs: per profile, in order, and gone with the tab. */
class DesktopCompatibilityTabViewTest {
    private val boardUrl = "https://may.2chan.net/b/"
    private fun threadUrl(no: Int) = "${boardUrl}res/$no.htm"
    private fun tab(no: Int) = CompatTab(
        compatTabKey(threadUrl(no)), threadUrl(no), threadUrl(no), compatBoardKey(boardUrl), "板", "$no", "スレ$no",
        replyCount = 1, insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L
    )
    private fun view(no: Int, profile: String, ordinal: Int) = CompatSharedTabView(compatTabKey(threadUrl(no)), profile, ordinal, 5L)

    @Test
    fun viewsAreKeptPerProfileInOrderAndGoWithTheirTab() = runBlocking {
        val root = Files.createTempDirectory("tab-view").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            store.importModernBoards(listOf(BoardSummary("m", "板", "", boardUrl, "")))
            (1..3).forEach { store.openTab(tab(it)) }
            store.replaceTabViews("futaber", listOf(view(2, "futaber", 1), view(1, "futaber", 0), view(9, "futaber", 2)))
            // The view of a tab that does not exist is dropped; the rest comes back in order.
            assertEquals(listOf(compatTabKey(threadUrl(1)), compatTabKey(threadUrl(2))), store.loadTabViews("futaber").map { it.tabKey })
            // Another profile is untouched by a replace.
            store.replaceTabViews("ftutter", listOf(view(3, "ftutter", 0)))
            store.replaceTabViews("futaber", listOf(view(3, "futaber", 0)))
            assertEquals(listOf(compatTabKey(threadUrl(3))), store.loadTabViews("futaber").map { it.tabKey })
            assertEquals(1, store.loadTabViews("ftutter").size)
            // Closing the tab takes its views along.
            store.closeTabs(setOf(compatTabKey(threadUrl(3))), nowEpochMillis = 10L)
            assertTrue(store.loadTabViews("futaber").isEmpty())
            assertTrue(store.loadTabViews("ftutter").isEmpty())
        } finally { store.close(); root.deleteRecursively() }
    }
}
