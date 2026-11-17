package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatReplyDraft
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** D-B6: this mode's reply drafts as shared drafts, hanging on the shared tab, without overwriting newer ones. */
class FutaberSharedDraftsTest {
    private val may = BoardSummary("may-b", "二次元裏", "", "https://may.2chan.net/b/futaba.php", "")
    private val tutorial = BoardSummary("t", "チュートリアル", "", "https://www.example.com/t/futaba.php", "")
    private fun draft(key: String, text: String, at: Long) = FutaberDraft(key = key, comment = text, updatedAt = at)

    @Test
    fun onlyTextRepliesOfOfficialBoardsAreShared() {
        val plan = futaberSharedDraftsPlan(
            listOf(draft("r:may-b:1", "本文", 5), draft("r:may-b:2", "", 6), draft("c:may-b", "新規", 7), draft("r:t:3", "x", 8)),
            listOf(may, tutorial)
        )
        assertEquals(setOf(compatTabKey("https://may.2chan.net/b/res/1.htm")), plan.keys)
        assertEquals("本文", plan.values.single().comment)
    }

    @Test
    fun applyingWritesOnlyOnAnExistingTabKeepsANewerDraftAndRemovesWhatWasDiscarded() = runBlocking {
        val root = Files.createTempDirectory("futaber-shared-drafts").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            store.importModernBoards(listOf(may))
            val boardKey = compatBoardKey("https://may.2chan.net/b/")
            fun tab(id: String): CompatTab {
                val url = "https://may.2chan.net/b/res/$id.htm"
                return CompatTab(compatTabKey(url), url, url, boardKey, "二次元裏", id, "t$id", replyCount = 1, insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L)
            }
            store.addTabsIfAbsent(listOf(tab("1"), tab("2")))
            val k1 = tab("1").key
            val k2 = tab("2").key
            val k3 = compatTabKey("https://may.2chan.net/b/res/3.htm")
            store.saveDraft(CompatReplyDraft(k2, comment = "他モードの新しい下書き", updatedAtEpochMillis = 900L))
            val plan = futaberSharedDraftsPlan(
                listOf(draft("r:may-b:1", "ふたばー風", 100), draft("r:may-b:2", "古い", 100), draft("r:may-b:3", "タブ無し", 100)),
                listOf(may)
            )
            val shared = futaberApplySharedDrafts(store, plan, emptySet())
            assertEquals("ふたばー風", store.loadDraft(k1)?.comment)
            assertEquals("他モードの新しい下書き", store.loadDraft(k2)?.comment)
            assertNull(store.loadDraft(k3))
            assertEquals(setOf(k1), shared)
            // The draft was sent or thrown away: what this mode shared goes.
            val after = futaberApplySharedDrafts(store, emptyMap(), shared)
            assertNull(store.loadDraft(k1))
            assertEquals(emptySet(), after)
            assertNotNull(store.loadDraft(k2))
        } finally {
            root.deleteRecursively()
        }
        Unit
    }
}

/** D-B6 (J5): another mode's draft starts the reply when this mode has none. */
class FutaberSharedDraftAdoptionTest {
    private val theirs = CompatReplyDraft("tab", subject = "題", comment = "他モードの下書き", updatedAtEpochMillis = 5L)

    @Test
    fun theirDraftIsTakenOnlyWhenThereIsNothingOfOurOwnAndNothingTypedYet() {
        assertEquals(FutaberPostStart("題", "他モードの下書き"), futaberSharedDraftToAdopt(null, theirs, "", ""))
        assertEquals(FutaberPostStart("題", "他モードの下書き"), futaberSharedDraftToAdopt(FutaberDraft("k"), theirs, "", ""))
        assertNull(futaberSharedDraftToAdopt(FutaberDraft("k", comment = "自分の"), theirs, "", ""))
        assertNull(futaberSharedDraftToAdopt(null, theirs, "", ">>1\n"))
        assertNull(futaberSharedDraftToAdopt(null, null, "", ""))
        assertNull(futaberSharedDraftToAdopt(null, theirs.copy(subject = "", comment = " "), "", ""))
    }
}
