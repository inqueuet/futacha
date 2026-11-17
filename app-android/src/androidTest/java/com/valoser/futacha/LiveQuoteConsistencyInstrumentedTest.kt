@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.util.Log
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.repo.createRemoteBoardRepository
import com.valoser.futacha.shared.ui.board.buildThreadPostDerivedData
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Read-only check against the live may/img boards on a real device (GET only, throttled): every post the
 * parser says has replies must list them when the count is tapped, and every quote must show a source.
 * Skipped unless the runner argument `liveQuoteAudit` is set, so normal instrumentation runs stay offline.
 */
class LiveQuoteConsistencyInstrumentedTest {
    @Test fun liveBoardsReplyCountsAreReachableAndQuotesShowTheirSource() = runBlocking {
        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        if (args.getString("liveQuoteAudit") == null) return@runBlocking
        val perBoard = args.getString("liveThreadsPerBoard")?.toInt() ?: 60
        val repo = createRemoteBoardRepository()
        var emptyTaps = 0; var emptyQuotes = 0; var countedPosts = 0; var posts = 0; var threads = 0
        val problems = mutableListOf<String>()
        try {
            for (board in listOf("https://may.2chan.net/b/", "https://img.2chan.net/b/")) {
                val items = repo.getCatalog(board, CatalogMode.Catalog).take(perBoard)
                Log.i("LIVE_QUOTE", "board=$board threads=${items.size}")
                for (item in items) {
                    delay(700)
                    val page = runCatching { repo.getThread(board, item.id) }.getOrNull() ?: continue
                    threads++
                    val snap = page.toCompatThreadSnapshot("audit", 0L).posts
                    posts += snap.size
                    val derived = buildThreadPostDerivedData(page.posts)
                    for (p in snap) {
                        if (p.referencedCount <= 0) continue
                        countedPosts++
                        if (extractCompatHeaderPosts(snap, p, CompatHeaderExtractionKind.QUOTE).isEmpty()) {
                            emptyTaps++; problems += "count-tap-empty $board/${item.id} No.${p.postNo}"
                        }
                        assertEquals("list/count $board/${item.id} No.${p.postNo}", p.referencedCount, derived.referencedByMap[p.postNo].orEmpty().size)
                    }
                    for (q in snap) for (ref in q.quoteReferences) {
                        val queries = ref.text.lines().mapNotNull(::compatQuoteQueryForLine)
                        if (queries.flatMap { resolveCompatQuotePosts(snap, q.position, it) }.isEmpty()) {
                            emptyQuotes++; problems += "quote-empty $board/${item.id} reply=No.${q.postNo} text=${ref.text.take(60)}"
                        }
                    }
                }
            }
        } finally { repo.close() }
        problems.forEach { Log.w("LIVE_QUOTE", it) }
        Log.i("LIVE_QUOTE", "SUMMARY threads=$threads posts=$posts postsWithCount=$countedPosts emptyCountTaps=$emptyTaps emptyQuotes=$emptyQuotes")
        assertEquals(problems.joinToString("\n"), 0, emptyTaps + emptyQuotes)
    }
}
