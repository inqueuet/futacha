package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.repo.createRemoteBoardRepository
import com.valoser.futacha.shared.ui.board.buildThreadPostDerivedData
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test

/**
 * Read-only audit against live boards (GET only, throttled). Runs only with FUTACHA_LIVE_QUOTE_AUDIT=<out file>.
 * Compares the parsed reply count with what tapping it / tapping the quote actually resolves in both modes.
 */
class LiveQuoteConsistencyAuditTest {
    @Test
    fun auditLiveBoards() = runBlocking {
        val out = System.getenv("FUTACHA_LIVE_QUOTE_AUDIT") ?: return@runBlocking
        val boards = (System.getenv("FUTACHA_LIVE_BOARDS") ?: "https://may.2chan.net/b/,https://img.2chan.net/b/").split(",")
        val delayMs = (System.getenv("FUTACHA_LIVE_DELAY_MS") ?: "800").toLong()
        val log = File(out).also { it.writeText("") }
        fun w(s: String) { log.appendText(s + "\n"); println(s) }
        val repo = createRemoteBoardRepository()
        try {
            for (board in boards) {
                val items = runCatching { repo.getCatalog(board, CatalogMode.Catalog) }.getOrElse {
                    w("CATALOG-FAIL $board ${it::class.simpleName}: ${it.message}"); emptyList()
                }
                w("BOARD $board threads=${items.size}")
                var threads = 0; var posts = 0; var withCount = 0
                var tapNoReplies = 0; var tapCountMismatch = 0; var quoteNoSource = 0; var futachaMismatch = 0
                for (item in items) {
                    delay(delayMs)
                    val page = runCatching { repo.getThread(board, item.id) }.getOrElse {
                        w("THREAD-FAIL $board/${item.id} ${it::class.simpleName}: ${it.message}"); continue
                    }
                    threads++
                    val snap = page.toCompatThreadSnapshot("audit", 0L).posts
                    posts += snap.size
                    val derived = buildThreadPostDerivedData(page.posts)
                    for (p in snap) {
                        if (p.referencedCount <= 0) continue
                        withCount++
                        val tapped = extractCompatHeaderPosts(snap, p, CompatHeaderExtractionKind.QUOTE)
                        val fut = derived.referencedByMap[p.postNo].orEmpty()
                        fun detail(): String {
                            val lost = fut.filter { f -> tapped.none { it.postNo == f.id } }
                            val srcLines = p.messageHtml.toCompatPlainText().lines().map { it.trim() }.filter { it.isNotBlank() }.take(4)
                            val quoters = lost.joinToString(" || ") { f ->
                                val qs = snap.first { it.postNo == f.id }
                                val ql = qs.messageHtml.toCompatPlainText().lines().filter { it.trimStart().startsWith(">") || it.trimStart().startsWith("＞") }
                                "No.${f.id} quoteLines=$ql queries=${ql.mapNotNull(::compatQuoteQueryForLine)}"
                            }
                            return "src.posterId=${p.posterId} src.lines=$srcLines src.file=${compatPostMediaFileNames(p)} LOST: $quoters"
                        }
                        if (tapped.isEmpty() || tapped.size < p.referencedCount) w("DETAIL $board/${item.id} No.${p.postNo} ${detail()}")
                        if (tapped.isEmpty()) {
                            tapNoReplies++
                            w("TOSHIAKI-TAP-EMPTY $board/${item.id} No.${p.postNo} count=${p.referencedCount} futachaList=${fut.map { it.id }}")
                        } else if (tapped.size != p.referencedCount) {
                            tapCountMismatch++
                            w("TOSHIAKI-COUNT-DIFF $board/${item.id} No.${p.postNo} count=${p.referencedCount} tapped=${tapped.map { it.postNo }}")
                        }
                        if (fut.size != p.referencedCount) {
                            futachaMismatch++
                            w("FUTACHA-LIST-DIFF $board/${item.id} No.${p.postNo} count=${p.referencedCount} list=${fut.map { it.id }}")
                        }
                    }
                    for (q in snap) {
                        // The user taps one quote block; a block that resolves to nothing shows no source at all.
                        for (ref in q.quoteReferences) {
                            val queries = ref.text.lines().mapNotNull(::compatQuoteQueryForLine)
                            val shown = queries.flatMap { resolveCompatQuotePosts(snap, q.position, it) }
                            if (shown.isEmpty()) {
                                quoteNoSource++
                                w("QUOTE-TAP-EMPTY $board/${item.id} reply=No.${q.postNo} parserTargets=${ref.targetPostIds} text=${ref.text.replace("\n", "⏎")} queries=${queries.map { it.replace("\n", "⏎") }}")
                            }
                        }
                    }
                }
                w("SUMMARY $board threads=$threads posts=$posts postsWithCount=$withCount tapEmpty=$tapNoReplies countDiff=$tapCountMismatch futachaDiff=$futachaMismatch quoteNoSource=$quoteNoSource")
            }
        } finally { repo.close() }
    }
}
