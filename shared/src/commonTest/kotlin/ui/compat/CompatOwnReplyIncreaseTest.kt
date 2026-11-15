package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatOwnReplyIncreaseTest {
    private fun post(no: String, html: String) = CompatPostSnapshot(position = no.toInt(), postNo = no, timestamp = "t", messageHtml = html)

    @Test
    fun theFirstLoadNeverNotifies() {
        val fetched = listOf(post("1", "自分の書き込み"), post("2", "&gt;&gt;1 返信"))
        assertTrue(compatOwnReplyIncreases(null, fetched, setOf("1")).isEmpty())
    }

    @Test
    fun newRepliesToAnOwnPostAreCountedWithItsText() {
        val previous = listOf(post("1", "自分の書き込み"), post("2", "&gt;&gt;1 古い返信"))
        val fetched = previous + post("3", "&gt;&gt;1 新しい返信") + post("4", "&gt;No.1<br>同意") + post("5", "無関係")
        val increases = compatOwnReplyIncreases(previous, fetched, setOf("1"))
        assertEquals(1, increases.size)
        assertEquals("1", increases[0].postNo)
        assertEquals(2, increases[0].count)
        assertEquals("自分の書き込み", increases[0].excerpt)
    }

    @Test
    fun repliesToOthersDoNotNotify() {
        val previous = listOf(post("1", "自分"), post("2", "他人"))
        val fetched = previous + post("3", "&gt;&gt;2 返信")
        assertTrue(compatOwnReplyIncreases(previous, fetched, setOf("1")).isEmpty())
    }
}
