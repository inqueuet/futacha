package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.ui.compat.computeCompatImagePhashFromBytes
import com.valoser.futacha.shared.ui.compat.collectCompatImagePhashes
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.*

/** Regression tests for the eighth MIX audit. */
class MixEighthAuditTest {
    @Test fun mixResolvesOriginalBeforeCachingAndNormalCatalogReusesCorrectHash() = runBlocking {
        fun picture(diagonal: Boolean): ByteArray {
            val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB)
            for (x in 0..31) for (y in 0..31) image.setRGB(x, y,
                if (if (diagonal) x / 4 == y / 4 else x < 16) 0xffffff else 0x112244)
            return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        }
        val original = picture(true)
        val thumb = picture(false)
        val expected = computeCompatImagePhashFromBytes(original)
        assertNotEquals(expected, computeCompatImagePhashFromBytes(thumb))
        val base = "https://may.2chan.net/b/audit-eight-cache-${System.nanoTime()}"
        var resolutions = 0
        HttpClient(MockEngine { request ->
            when {
                request.url.encodedPath.endsWith(".png") -> respond(original)
                request.url.encodedPath.contains("thumb") -> respond(thumb)
                else -> respondError(HttpStatusCode.NotFound)
            }
        }).use { client ->
            val candidates = listOf("row" to "$base/source.jpg")
            val entry = row("https://may.2chan.net/b/", "row", "$base/source.jpg", "$base/thumb.jpg")
            val mix = collectCompatImagePhashes(client, candidates, fallbackImageUrl = { entry.item.thumbnailUrl },
                resolveMissingUrl = { resolveMixMissingImageUrl(entry) { board, id ->
                    assertEquals(entry.board.url, board); assertEquals("row", id); "$base/source.png"
                } })
            val normal = collectCompatImagePhashes(client, candidates, fallbackImageUrl = { "$base/thumb.jpg" },
                resolveMissingUrl = { resolutions++; "$base/source.png" })
            assertEquals(0, resolutions)
            assertEquals(mix, normal)
            assertEquals(expected, normal.getValue("row"))
        }
    }

    @Test fun missingGuessedJpegRecoversInBothNormalAndMixCatalogs() = runBlocking {
        val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB)
        for (x in 0..31) for (y in 0..31) image.setRGB(x, y, if (x / 4 == y / 4) 0xffffff else 0x112244)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        val base = "https://may.2chan.net/b/audit-eight-${System.nanoTime()}"
        val requested = mutableListOf<String>()
        HttpClient(MockEngine { request ->
            requested += request.url.toString()
            if (request.url.encodedPath.endsWith(".png"))
                respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/png"))
            else respondError(HttpStatusCode.NotFound)
        }).use { client ->
            // Same resolver used by FutachaMixScreen, on a cold cache.
            val mix = collectCompatImagePhashes(client, listOf("mix" to "$base/mix.jpg"),
                fallbackImageUrl = { "$base/thumb-mix.jpg" },
                resolveMissingUrl = { resolveMixMissingImageUrl(row("https://may.2chan.net/b/", "mix", "$base/mix.jpg", "$base/thumb-mix.jpg")) { _, _ -> "$base/mix.png" } })
            assertEquals(computeCompatImagePhashFromBytes(bytes), mix.getValue("mix"))
            assertTrue(requested.contains("$base/mix.png"))
            // The normal catalog supplies the thread-head resolver; use a distinct URL to avoid cache reuse.
            val normal = collectCompatImagePhashes(client, listOf("normal" to "$base/normal.jpg"),
                fallbackImageUrl = { "$base/thumb-normal.jpg" },
                resolveMissingUrl = { "$base/normal.png" })
            assertEquals(computeCompatImagePhashFromBytes(bytes), normal.getValue("normal"))
            assertTrue(requested.contains("$base/normal.png"))
        }
    }
    private fun row(board: String, id: String, image: String? = "$board$id.jpg", thumb: String? = null) = MixEntry(
        BoardSummary(board, board, "", board, ""), CatalogItem(id, "${board}res/$id.htm", id, thumb, image, replyCount = 1),
        MixEntryKey(board, id), null, false, 0, 80)

    @Test fun budgetIsSharedEquallyEvenWhenDisplayOrderGroupsBoards() {
        val boards = (1..4).map { "https://s$it.2chan.net/b/" }
        val rows = boards.flatMap { board -> (1..80).map { row(board, "$it") } }
        val rules = listOf(CompatNgRule("image", CompatNgKind.CATALOG_IMAGE_PHASH, "*", "0000000000000000", 0L))
        val candidates = planMixImageNgCandidates(rows, rules, boards.reversed())
        assertEquals(256, candidates.size)
        assertEquals(boards.reversed(), candidates.take(4).map { it.first.substringBefore('#') })
        assertEquals(boards.associateWith { 64 }, candidates.groupingBy { it.first.substringBefore('#') }.eachCount())
        assertEquals(rows.size, planMixImageNgCandidates(rows, rules, boards, 1000).size)
    }

    @Test fun ineligibleRowsDoNotConsumeBudgetAndBoardScopesStaySeparate() {
        val board = "https://may.2chan.net/b/"
        val other = "https://img.2chan.net/b/"
        val rules = listOf(CompatNgRule("image", CompatNgKind.CATALOG_IMAGE_PHASH, compatBoardKey(board), "0000000000000000", 0L))
        val rows = (1..300).map { row(board, "$it", null) } + row(other, "301") + row(board, "302")
        assertEquals(listOf(rows.last().key.asString() to rows.last().item.fullImageUrl),
            planMixImageNgCandidates(rows, rules, listOf(other, board)))
        assertTrue(planMixImageNgCandidates(rows, emptyList(), listOf(board)).isEmpty())
    }

    @Test fun aimogeDoesNotFetchThreadHeadAndCancellationIsPreserved(): Unit = runBlocking {
        val aimoge = row("https://nijiurachan.net/b/test/", "uuid", "image", "thumb")
        assertEquals("thumb", resolveMixMissingImageUrl(aimoge) { _, _ -> error("No head request for Aimoge") })
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            resolveMixMissingImageUrl(row("https://may.2chan.net/b/", "1")) { _, _ -> throw kotlinx.coroutines.CancellationException() }
        }
    }

}
