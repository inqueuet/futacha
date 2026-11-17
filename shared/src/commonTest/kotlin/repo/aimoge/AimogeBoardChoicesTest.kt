package com.valoser.futacha.shared.repo.aimoge

import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AM-B5: the add-board list is made from `/boards` (research ch.17: 15 boards, 3 hidden, 9 allow R18). */
class AimogeBoardChoicesTest {
    private val hidden = setOf("gore", "test", "test2")
    private val unchecked = setOf("bl", "cat", "live")
    private val r18 = setOf("ai2", "aimg", "bl", "dark", "gore", "live", "test", "test2x", "tough", "touhou").minus("test2x")
    private val slugs = listOf("ai2", "aimg", "bl", "cat", "dark", "gore", "live", "test", "test2", "tough", "touhou",
        "img", "jun", "may", "news")

    private fun json(): String = slugs.joinToString(",", "{\"boards\":[", "]}") { s ->
        """{"slug":"$s","boardName":"板$s","allowR18ThreadCreation":${s in r18},"presentation":{"defaultVisible":${s !in hidden},"defaultEnabled":${s !in unchecked},"imageUrl":"https://nijiurachan.net/assets/icon_board-$s.webp"}}"""
    }

    @Test
    fun twelveBoardsAreOfferedAndTheDefaultsDecideTheChecks() = kotlinx.coroutines.test.runTest {
        val all = AimogeBoardRepositoryTestSupport.repoAnswering(json()).boards()
        assertEquals(15, all.size)
        val choices = aimogeBoardChoices(all, registeredUrls = emptySet())
        assertEquals(12, choices.size)
        assertTrue(choices.none { it.slug in hidden })
        assertEquals(slugs.filter { it !in hidden }, choices.map { it.slug })
        val checks = aimogeInitialChecks(choices)
        assertEquals(9, checks.size)
        assertTrue(choices.filter { it.slug in unchecked }.none { it.url in checks })
        assertEquals("https://nijiurachan.net/assets/icon_board-ai2.webp", all.first().iconUrl)
    }

    @Test
    fun registeredBoardsAreLeftOutAndR18NeedsAConfirmation() = kotlinx.coroutines.test.runTest {
        val all = AimogeBoardRepositoryTestSupport.repoAnswering(json()).boards()
        val choices = aimogeBoardChoices(all, registeredUrls = setOf("https://nijiurachan.net/b/ai2/"))
        assertFalse(choices.any { it.slug == "ai2" })
        val none = aimogeR18Checked(choices, setOf("https://nijiurachan.net/b/cat/"))
        assertTrue(none.isEmpty())
        val some = aimogeR18Checked(choices, setOf("https://nijiurachan.net/b/cat/", "https://nijiurachan.net/b/dark/"))
        assertEquals(listOf("dark"), some.map { it.slug })
    }

    @Test
    fun anIconOffTheSiteIsDroppedAndMissingFieldsTakeTheDefaults() = kotlinx.coroutines.test.runTest {
        val body = """{"boards":[{"slug":"x","presentation":{"imageUrl":"http://evil.example/a.png"}},{"slug":"y"}]}"""
        val all = AimogeBoardRepositoryTestSupport.repoAnswering(body).boards()
        assertNull(all[0].iconUrl)
        assertEquals("x", all[0].name)
        assertTrue(all[1].defaultVisible && all[1].defaultEnabled && !all[1].r18)
    }
}

internal object AimogeBoardRepositoryTestSupport {
    fun repoAnswering(body: String): AimogeBoardRepository {
        val engine = io.ktor.client.engine.mock.MockEngine {
            respond(body, io.ktor.http.HttpStatusCode.OK, io.ktor.http.headersOf(io.ktor.http.HttpHeaders.ContentType, "application/json"))
        }
        return AimogeBoardRepository(io.ktor.client.HttpClient(engine), nowMillis = { 0L })
    }
}
