package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals

class FutachaRemoteResultMessageTest {
    @Test
    fun futabasOwnReplyIsAddedAsItCame() {
        assertEquals("そうだねを送信しました（ふたばの返事：3）", futachaRemoteResultMessage("そうだねを送信しました", "3\n"))
        assertEquals("そうだねを送信しました（ふたばの返事：0）", futachaRemoteResultMessage("そうだねを送信しました", "0"))
        assertEquals("DEL依頼を送信しました（ふたばの返事：ok）",
            futachaRemoteResultMessage("DEL依頼を送信しました", "<html><body>ok</body></html>"))
    }

    @Test
    fun anEmptyReplyAddsNothingAndALongOneIsCut() {
        assertEquals("DEL依頼を送信しました", futachaRemoteResultMessage("DEL依頼を送信しました", ""))
        assertEquals("DEL依頼を送信しました", futachaRemoteResultMessage("DEL依頼を送信しました", null))
        val long = "あ".repeat(200)
        assertEquals("x（ふたばの返事：${"あ".repeat(FUTACHA_REMOTE_REPLY_MAX_CHARS)}…）", futachaRemoteResultMessage("x", long))
    }
}
