package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FutaberCompatKeysTest {
    private val may = BoardSummary(id = "may-b", name = "may/b", category = "", url = "https://may.2chan.net/b/futaba.php", description = "")
    private val mock = BoardSummary(id = "t", name = "チュートリアル", category = "", url = "https://www.example.com/t/futaba.php", description = "")

    @Test
    fun keysAreTheOnesTheOtherModesDeriveFromTheCanonicalAddresses() {
        // ふたちゃ: boardKey = compatBoardKey(canonicalizeBoardUrl(url)), tabKey = compatTabKey("${canonical}res/$id.htm").
        assertEquals("https://may.2chan.net/b/", canonicalizeBoardUrl(may.url))
        assertEquals(compatBoardKey("https://may.2chan.net/b/"), futaberCompatBoardKey(may))
        assertEquals("https://may.2chan.net/b/res/123.htm", futaberCompatThreadUrl(may, "123"))
        assertEquals(compatTabKey("https://may.2chan.net/b/res/123.htm"), futaberCompatTabKey(may, "123"))
        // The raw address would have produced a key nothing else uses.
        assertNotEquals(compatBoardKey(may.url), futaberCompatBoardKey(may))
    }

    @Test
    fun aBoardAddressWithoutTheScriptNameGivesTheSameKey() {
        val bare = may.copy(url = "https://may.2chan.net/b/")
        assertEquals(futaberCompatBoardKey(may), futaberCompatBoardKey(bare))
    }

    @Test
    fun aBoardThatIsNotAnOfficialOneStillGetsAStableKey() {
        assertEquals(futaberCompatBoardKey(mock), futaberCompatBoardKey(mock))
        assertEquals(futaberCompatTabKey(mock, "1"), futaberCompatTabKey(mock, "1"))
        assertNotEquals(futaberCompatTabKey(mock, "1"), futaberCompatTabKey(mock, "2"))
    }

    @Test
    fun aThreadRuleMadeByAnotherModeUnderTheCanonicalKeyHidesThePostHere() {
        val posts = listOf(
            Post(id = "1", author = null, subject = null, timestamp = "25/10/05(日)12:00:00", messageHtml = "普通", imageUrl = null, thumbnailUrl = null),
            Post(id = "2", author = null, subject = null, timestamp = "25/10/05(日)12:00:00", messageHtml = "禁止ワード", imageUrl = null, thumbnailUrl = null)
        )
        // Another mode stores the rule under compatTabKey of the canonical thread address.
        val otherModeKey = compatTabKey("https://may.2chan.net/b/res/123.htm")
        val rule = CompatNgRule("r", CompatNgKind.THREAD_WORD, otherModeKey, "禁止ワード", 0L)
        val hidden = futaberNgHiddenIds(
            posts, emptyList(), emptyList(), listOf(rule), futaberCompatTabKey(may, "123"), futaberCompatBoardKey(may)
        )
        assertEquals(setOf("2"), hidden)
    }
}
