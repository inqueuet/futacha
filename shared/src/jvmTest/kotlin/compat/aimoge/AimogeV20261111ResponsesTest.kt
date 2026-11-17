package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.network.aimoge.AIMOGE_API_VERSION
import com.valoser.futacha.shared.network.aimoge.aimogePosts
import com.valoser.futacha.shared.network.aimoge.decodeAimogeCatalog
import com.valoser.futacha.shared.network.aimoge.decodeAimogeState
import com.valoser.futacha.shared.network.aimoge.decodeAimogeSummary
import com.valoser.futacha.shared.network.aimoge.decodeAimogeThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * U3: real answers of the 2026-11-11 version (read-only GETs of board aimg on 2026-11-11, X-Board-Key sent) are
 * read by the same decoders the app uses.
 */
class AimogeV20261111ResponsesTest {
    private fun res(name: String) =
        checkNotNull(javaClass.getResource("/aimoge-v20261111/$name")) { name }.readText()

    @Test fun theAppAsksForTheCurrentVersion() = assertEquals("2026-11-11", AIMOGE_API_VERSION)

    @Test fun theCatalogThreadStateAndSummaryOfTheNewVersionAreRead() {
        val catalog = decodeAimogeCatalog(res("catalog.json"))
        assertTrue(catalog.threads.isNotEmpty() && catalog.threads.all { it.id.isNotBlank() })
        val thread = decodeAimogeThread(res("thread.json"))
        assertTrue(thread.posts.isNotEmpty())
        assertTrue(thread.posts.all { it.status == "public" || it.status == "shadowed" })
        assertEquals(thread.posts.size, aimogePosts(thread.posts).size)
        val state = decodeAimogeState(res("state.json"))
        assertTrue(state.newPosts.isNotEmpty())
        val summary = decodeAimogeSummary(res("summary.json"))
        assertEquals(thread.id, summary.single().id)
    }
}
