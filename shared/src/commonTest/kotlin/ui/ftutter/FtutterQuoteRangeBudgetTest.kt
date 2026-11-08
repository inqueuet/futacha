package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.ui.board.messageHtmlToLines
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/** Audit C1: the quote ranges are found as before, but a troll's post of hundreds of quote lines no longer runs for minutes. */
class FtutterQuoteRangeBudgetTest {
    private fun q(text: String) = "<font color=\"#789922\">&gt;$text</font>"
    private fun post(id: String, html: String) = Post(
        id = id, author = null, subject = null, timestamp = "", messageHtml = html, imageUrl = null, thumbnailUrl = null
    )

    @Test
    fun randomThreadsGetTheSameLinksAsTryingEveryRange() {
        val words = listOf("あ", "い", "うえ", "お", "かき", "No.3", "x")
        repeat(300) { seed ->
            val random = Random(seed)
            val posts = (0 until 8).map { i ->
                val lines = (0 until random.nextInt(1, 6)).map {
                    val text = (0 until random.nextInt(1, 3)).joinToString("") { words.random(random) }
                    if (i > 0 && random.nextInt(3) == 0) q(text) else text
                }
                post("${i + 1}", lines.joinToString("<br>"))
            }
            assertEquals(exhaustiveLinks(posts), ftutterRelinkQuotes(posts).map { it.quoteReferences }, "seed $seed")
        }
    }

    @Test
    fun aPostOfHundredsOfQuoteLinesIsLinkedQuickly() {
        val earlier = (1..200).map { post("$it", "あ<br>い<br>本文$it") }
        // Every line matches by itself, no two lines together do (the worst case for trying ranges).
        val troll = post("201", (1..400).joinToString("<br>") { q(if (it % 2 == 0) "あ" else "い") } + "<br>荒らし")
        val started = TimeSource.Monotonic.markNow()
        val linked = ftutterRelinkQuotes(earlier + troll).last().quoteReferences
        assertTrue(started.elapsedNow().inWholeSeconds < 10, "took ${started.elapsedNow()}")
        assertEquals(400, linked.size)
        assertTrue(linked.all { it.targetPostIds.isNotEmpty() })
    }

    @Test
    fun aCancelStopsTheWork() {
        val posts = (1..50).map { post("$it", "${q("あ")}<br>本文") }
        var calls = 0
        assertFailsWith<IllegalStateException> {
            ftutterRelinkQuotes(posts) { if (++calls > 5) throw IllegalStateException("cancelled") }
        }
    }

    // ---- The algorithm before C1 (every range, longest first), kept here as the reference. ----

    private class Text(val body: String, val bodyWithoutQuotes: String, val fileName: String?, val lines: List<String>)

    private fun exhaustiveLinks(posts: List<Post>): List<List<QuoteReference>> {
        val texts = posts.map { p ->
            val lines = messageHtmlToLines(p.messageHtml).map { it.trimEnd() }
            Text(lines.joinToString("\n"), lines.filterNot(::ftutterIsQuoteLine).joinToString("\n"),
                p.imageUrl?.substringBefore('?')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }, lines)
        }
        fun query(line: String) = line.trim().drop(1)
        fun find(index: Int, query: String): Int? {
            if (query.isBlank()) return null
            val earlier = (index - 1) downTo 0
            Regex("^No\\.(\\d+)$").matchEntire(query)?.let { m -> earlier.firstOrNull { posts[it].id == m.groupValues[1] }?.let { return it } }
            earlier.firstOrNull { texts[it].fileName == query }?.let { return it }
            ((index + 1) until posts.size).firstOrNull { texts[it].fileName == query }?.let { return it }
            earlier.firstOrNull { texts[it].body == query }?.let { return it }
            earlier.firstOrNull { texts[it].bodyWithoutQuotes.contains(query) }?.let { return it }
            earlier.firstOrNull { texts[it].body.contains(query) }?.let { return it }
            return null
        }
        fun range(index: Int, lines: List<String>, from: Int, until: Int, out: MutableList<QuoteReference>) {
            if (from >= until) return
            for (length in (until - from) downTo 1) for (start in from..(until - length)) {
                val r = lines.subList(start, start + length)
                val t = find(index, r.joinToString("\n") { query(it) })
                if (t != null) {
                    range(index, lines, from, start, out)
                    out += QuoteReference(r.joinToString("\n"), listOf(posts[t].id))
                    range(index, lines, start + length, until, out)
                    return
                }
            }
            out += QuoteReference(lines.subList(from, until).joinToString("\n"), emptyList())
        }
        return posts.indices.map { index ->
            val blocks = mutableListOf<List<String>>()
            var current = mutableListOf<String>()
            texts[index].lines.forEach { line ->
                if (ftutterIsQuoteLine(line)) current += line.trim()
                else if (current.isNotEmpty()) { blocks += current; current = mutableListOf() }
            }
            if (current.isNotEmpty()) blocks += current
            val out = mutableListOf<QuoteReference>()
            blocks.forEach { block ->
                if (index == 0) { out += QuoteReference(block.joinToString("\n"), emptyList()); return@forEach }
                var run = mutableListOf<String>()
                fun flush() { if (run.isNotEmpty()) range(index, run, 0, run.size, out); run = mutableListOf() }
                block.forEach { line ->
                    val qq = query(line)
                    if (qq.startsWith("ID:", true) || qq.startsWith("IP:", true)) { flush(); out += QuoteReference(line, emptyList()) }
                    else run += line
                }
                flush()
            }
            out
        }
    }
}
