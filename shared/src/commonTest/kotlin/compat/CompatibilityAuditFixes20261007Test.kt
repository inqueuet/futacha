package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatibilityAuditFixes20261007Test {
    // M3: Post / Gallery / Viewer return to the workspace (and so the main screen or catalog) they came from.
    @Test
    fun postBackFollowsTheThreadWorkspaceOrigin() {
        val fromMain = CompatibilityWorkspaceState(
            host = CompatHost.Post("tab", CompatThreadOrigin.MAIN),
            // The catalog of a board the user visited earlier; the thread came from the drawer.
            catalogHostBoardKey = "boardA"
        )
        val workspace = reduceCompatibilityWorkspace(fromMain, CompatibilityEvent.Back).state
        assertEquals(CompatHost.ThreadWorkspace(CompatThreadOrigin.MAIN), workspace.host)
        assertEquals(CompatHost.Main, reduceCompatibilityWorkspace(workspace, CompatibilityEvent.Back).state.host)

        val fromCatalog = fromMain.copy(host = CompatHost.Post("tab", CompatThreadOrigin.CATALOG))
        val catalogWorkspace = reduceCompatibilityWorkspace(fromCatalog, CompatibilityEvent.Back).state
        assertEquals(
            CompatHost.Catalog("boardA"),
            reduceCompatibilityWorkspace(catalogWorkspace, CompatibilityEvent.Back).state.host
        )
        // The default is the previous behaviour (opened from a catalog).
        assertEquals(CompatThreadOrigin.CATALOG, CompatHost.Post("tab").threadOrigin)
    }

    @Test
    fun galleryAndViewerBackKeepTheOriginAcrossTheChain() {
        val origin = CompatThreadOrigin.DEEP_LINK
        val viewerFromGallery = CompatibilityWorkspaceState(
            host = CompatHost.Viewer("tab", 2, CompatViewerCaller.GALLERY, threadOrigin = origin)
        )
        val gallery = reduceCompatibilityWorkspace(viewerFromGallery, CompatibilityEvent.Back).state.host
        assertEquals(CompatHost.Gallery("tab", index = 2, threadOrigin = origin), gallery)
        val workspace = reduceCompatibilityWorkspace(viewerFromGallery.copy(host = gallery), CompatibilityEvent.Back).state.host
        assertEquals(CompatHost.ThreadWorkspace(origin), workspace)

        val viewerFromThread = viewerFromGallery.copy(
            host = CompatHost.Viewer("tab", 0, CompatViewerCaller.THREAD, threadOrigin = CompatThreadOrigin.MAIN)
        )
        assertEquals(
            CompatHost.ThreadWorkspace(CompatThreadOrigin.MAIN),
            reduceCompatibilityWorkspace(viewerFromThread, CompatibilityEvent.Back).state.host
        )
    }

    // 三巡目: URLs end at the first non-ASCII character; balanced parentheses stay.
    @Test
    fun inlineLinkStopsAtAFullWidthSpaceSoTheExtensionCheckStillWorks() {
        val url = "https://example.test/src/123.jpg"
        assertEquals(
            listOf(CompatInlineLink(3, 3 + url.length, url)),
            compatInlineLinks("本文 $url　これ")
        )
        assertEquals(
            listOf(CompatInlineLink(3, 3 + url.length, url)),
            compatInlineLinks("本文（$url）です")
        )
    }

    @Test
    fun inlineLinkKeepsAWikipediaStyleParenthesisAndDropsSentencePunctuation() {
        val url = "https://ja.wikipedia.org/wiki/A_(B)"
        assertEquals(listOf(CompatInlineLink(0, url.length, url)), compatInlineLinks("${url}。次の文"))
        assertEquals(listOf(CompatInlineLink(0, url.length, url)), compatInlineLinks("$url."))
        // A closing parenthesis that belongs to the sentence is not part of the URL.
        val plain = "https://example.test/a"
        assertEquals(listOf(CompatInlineLink(1, 1 + plain.length, plain)), compatInlineLinks("($plain)"))
    }

    // 低: a surrogate pair at the length limit is dropped whole.
    @Test
    fun thePairStraddlingTheLimitIsNotCutInHalf() {
        val word = "a" + "😀".repeat(12) // 25 UTF-16 units; the 10th pair starts at unit 19
        val cleaned = cleanCompatThreadReferenceWord(word, maxLength = 20)
        assertEquals(19, cleaned.length)
        assertTrue(cleaned.last().isLowSurrogate())
        assertEquals("abc", "abcdef".takeWithoutSplittingSurrogatePair(3))
        assertEquals("😀", "😀😀".takeWithoutSplittingSurrogatePair(3))
        assertEquals("😀😀", "😀😀".takeWithoutSplittingSurrogatePair(4))
        assertEquals("", "😀".takeWithoutSplittingSurrogatePair(1))
    }
}
