package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals

class FtutterPostBoxesTest {
    @Test fun choosingNothingOpensAnEmptyPostLikeTheOriginal() {
        assertEquals("", ftutterChosenQuote(listOf("a", "b"), emptySet()))
        assertEquals(">b\n", ftutterChosenQuote(listOf("a", "b"), setOf(1)))
    }

    @Test fun theCopyBoxSaysIdEvenForAnIpLikeTheOriginal() {
        val ip = listOf("IP" to "IP:130.62.*(transix.jp)")
        assertEquals(
            listOf("No" to "No.1", "ID" to "IP:130.62.*(transix.jp)", "本文" to "x"),
            ftutterCopyValues(null, "1", ip, null, "x", legacy = false)
        )
        assertEquals(
            listOf("E-Mail" to "sage", "No" to "No.1", "IP" to "IP:130.62.*(transix.jp)", "ファイル" to "a.jpg", "本文" to "x"),
            ftutterCopyValues("sage", "1", ip, "a.jpg", "x", legacy = true)
        )
        assertEquals(listOf("No" to "No.1", "本文" to "x"), ftutterCopyValues(null, "1", emptyList(), "", "x", legacy = false))
    }
}
