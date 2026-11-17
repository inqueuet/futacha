package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.repo.createRemoteBoardRepository
import kotlinx.cinterop.toKString
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import platform.posix.getenv
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Read-only check against the live may/img boards on the iOS runtime (GET only, throttled): every post whose
 * reply count is shown must list its replies, and every quote must show a source.
 * Runs only when the test process has FUTACHA_LIVE_QUOTE_AUDIT set (SIMCTL_CHILD_ prefix on the Simulator).
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class LiveQuoteConsistencyIosTest {
    @Test
    fun liveBoardsReplyCountsAreReachableAndQuotesShowTheirSource() = runBlocking {
        if (getenv("FUTACHA_LIVE_QUOTE_AUDIT") == null) return@runBlocking
        val perBoard = getenv("FUTACHA_LIVE_THREADS")?.toKString()?.toIntOrNull() ?: 60
        val repo = createRemoteBoardRepository()
        var threads = 0; var posts = 0; var counted = 0
        val problems = mutableListOf<String>()
        try {
            for (board in listOf("https://may.2chan.net/b/", "https://img.2chan.net/b/")) {
                val items = repo.getCatalog(board, CatalogMode.Catalog).take(perBoard)
                println("LIVE_QUOTE board=$board threads=${items.size}")
                for (item in items) {
                    delay(700)
                    val page = runCatching { repo.getThread(board, item.id) }.getOrNull() ?: continue
                    threads++
                    val snap = page.toCompatThreadSnapshot("audit", 0L).posts
                    posts += snap.size
                    for (p in snap) {
                        if (p.referencedCount <= 0) continue
                        counted++
                        if (extractCompatHeaderPosts(snap, p, CompatHeaderExtractionKind.QUOTE).isEmpty()) {
                            problems += "count-tap-empty $board/${item.id} No.${p.postNo}"
                        }
                    }
                    for (q in snap) for (ref in q.quoteReferences) {
                        val queries = ref.text.lines().mapNotNull(::compatQuoteQueryForLine)
                        if (queries.flatMap { resolveCompatQuotePosts(snap, q.position, it) }.isEmpty()) {
                            problems += "quote-empty $board/${item.id} reply=No.${q.postNo} text=${ref.text.take(60)}"
                        }
                    }
                }
            }
        } finally { repo.close() }
        problems.forEach { println("LIVE_QUOTE $it") }
        println("LIVE_QUOTE SUMMARY threads=$threads posts=$posts postsWithCount=$counted problems=${problems.size}")
        assertEquals(0, problems.size, problems.joinToString("\n"))
    }
}
