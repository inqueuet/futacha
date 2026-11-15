package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.graphics.Color
import com.valoser.futacha.shared.compat.compatInlineLinks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApuNonImageLinkTest {
    private val body = "ログ置いた fu1234567.txt と f123456.zip<br>画像は fu7654321.jpg<br>f2.50 は値段"

    @Test
    fun futachaLinksBareUploaderFilesOfAnyType() {
        // Before: ふたちゃ linked only full URLs, so a text or archive named in a post could not be opened.
        val annotated = buildAnnotatedMessageBase(body, emptyList(), Color.Green, Color.Blue)
        val urls = annotated.getStringAnnotations("url", 0, annotated.length).map { it.item }

        assertEquals(
            listOf(
                "https://dec.2chan.net/up2/src/fu1234567.txt",
                "https://dec.2chan.net/up/src/f123456.zip",
                "https://dec.2chan.net/up2/src/fu7654321.jpg"
            ),
            urls
        )
    }

    @Test
    fun toshiakiLinksBareUploaderFilesOfAnyType() {
        // Before: only image and video names were linked (`fu1234567.txt` stayed plain text).
        val urls = compatInlineLinks(body).map { it.url }

        assertTrue("https://dec.2chan.net/up2/src/fu1234567.txt" in urls)
        assertTrue("https://dec.2chan.net/up/src/f123456.zip" in urls)
        assertTrue("https://dec.2chan.net/up2/src/fu7654321.jpg" in urls)
        assertTrue(urls.none { "f2.50" in it })
    }

    @Test
    fun aFullUploaderUrlIsLinkedOnceNotAlsoAsItsBareName() {
        val html = "https://dec.2chan.net/up2/src/fu1234567.txt"
        val annotated = buildAnnotatedMessageBase(html, emptyList(), Color.Green, Color.Blue)
        assertEquals(listOf(html), annotated.getStringAnnotations("url", 0, annotated.length).map { it.item }, annotated.getStringAnnotations("url", 0, annotated.length).toString())
        assertEquals(listOf(html), compatInlineLinks(html).map { it.url })
    }
}
