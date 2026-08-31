package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompatPostSafetyTest {
    @Test
    fun destinationWarningIsOptInAndBoardSpecific() {
        val img = "https://img.2chan.net/b/"
        val may = "https://may.2chan.net/b/"

        assertNull(compatPostDestinationWarning(img, "としあき", enabled = false))
        assertEquals(
            "img板で「としあき」「スレあき」を含む投稿です。投稿先を確認してください。",
            compatPostDestinationWarning(img, "としあき", enabled = true)
        )
        assertEquals(
            "may板で「」を含む投稿です。投稿先を確認してください。",
            compatPostDestinationWarning(may, "「」", enabled = true)
        )
        assertEquals(
            "may板で「」を含む投稿です。投稿先を確認してください。",
            compatPostDestinationWarning(may, "「　 」", enabled = true)
        )
        assertNull(compatPostDestinationWarning(may, "「削除された記事が2件あります」", enabled = true))
        assertNull(compatPostDestinationWarning(may, "開き括弧「だけ", enabled = true))
        assertNull(compatPostDestinationWarning("https://dec.2chan.net/up/", "としあき「」", enabled = true))
    }
}
