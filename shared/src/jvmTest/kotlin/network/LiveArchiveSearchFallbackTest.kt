package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.repo.createRemoteBoardRepository
import com.valoser.futacha.shared.ui.board.ArchiveFallbackOutcome
import com.valoser.futacha.shared.ui.board.performThreadArchiveFallback
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test

/**
 * Read-only check against the live sites (GET only, throttled). Runs only with FUTACHA_LIVE_ARCHIVE_SEARCH=<out file>.
 * Searches words inqueuet may not have, and opens the first results the way a tap does (the archive fallback).
 */
class LiveArchiveSearchFallbackTest {
    @Test
    fun liveSearchFallsBackToOtherSitesAndTheResultsOpen() = runBlocking {
        val out = System.getenv("FUTACHA_LIVE_ARCHIVE_SEARCH") ?: return@runBlocking
        val log = File(out).also { it.writeText("") }
        fun w(s: String) { log.appendText(s + "\n"); println(s) }
        val client = com.valoser.futacha.shared.network.createHttpClient()
        val repo = createRemoteBoardRepository(client)
        val json = Json { ignoreUnknownKeys = true }
        try {
            val words = (System.getenv("FUTACHA_LIVE_ARCHIVE_WORDS") ?: "SKYRIM,アニメ,zzzzqqqq存在しない語").split(",")
            for ((board, boardWords) in listOf(
                "https://may.2chan.net/b/" to words,
                "https://img.2chan.net/b/" to words
            )) {
                val scope = extractArchiveSearchScope(board)
                for (word in boardWords) {
                    delay(800)
                    val items = runCatching { searchInqueuetArchiveThreads(client, json, word, scope, limit = 5) }
                    w("SEARCH $board \"$word\" -> ${items.map { r -> r.size.toString() + " " + r.map { it.htmlUrl.substringAfter("//").substringBefore("/") }.distinct() }.getOrElse { "ERROR ${it.message}" }}")
                    for (item in items.getOrNull().orEmpty().take(3)) {
                        delay(800)
                        val opened = runCatching {
                            performThreadArchiveFallback(client, repo, item.threadId, item.title, board, item.htmlUrl, json)
                        }
                        val summary = opened.map {
                            if (it is ArchiveFallbackOutcome.Success) "Success posts=${it.page.posts.size}" else it::class.simpleName
                        }.getOrElse { "ERROR ${it.message}" }
                        w("  OPEN ${item.threadId} replies=${item.replyCount} url=${item.htmlUrl} -> $summary")
                    }
                }
            }
        } finally { repo.close() }
    }
}
