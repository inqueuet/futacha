package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.createRemoteBoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.service.tryRefreshHistoryEntryFromArchive
import com.valoser.futacha.shared.ui.board.performThreadArchiveFallback
import com.valoser.futacha.shared.ui.compat.fetchCompatArchiveThreadPage
import com.valoser.futacha.shared.ui.compat.loadCompatThreadWithFallback
import com.valoser.futacha.shared.ui.futaber.futaberArchivedThread
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AM-B0 (docs/aimoge-support-plan-2026-11-10.md, AM-B3): the requests the five archive fallback call sites send today
 * when a Futaba thread (https://may.2chan.net/b/res/1439323740.htm) is gone (every 2chan thread page and every archive
 * answers 404). These must stay byte for byte after あいもげ support.
 *
 * - ui/board/ThreadArchiveFallbackSupport.kt:54 `performThreadArchiveFallback`, called directly.
 * - ui/futaber/FutaberThreadSupport.kt:306 `futaberArchivedThread`, called directly.
 * - ui/ftutter/FtutterThread.kt:850・853 is inside a Composable (`fetchArchive`); 850 calls `futaberArchivedThread`
 *   with `board.url` (853 only stores the result, no request), so it is fixed by the futaber golden.
 * - service/HistoryRefreshArchiveSupport.kt:122 `tryRefreshHistoryEntryFromArchive`, called directly.
 * - ui/compat/CompatThreadFetch.kt:154 is in `loadCompatThreadWithFallback`, called with the arguments
 *   CompatibilityApp.kt passes (cache off, the real `fetchCompatArchiveThreadPage` as the archive loader).
 */
class FutabaArchiveFallbackRequestsTest {
    private val board = FIXTURE_BOARD
    private val threadId = FIXTURE_THREAD_ID
    private val threadUrl = "${board}res/$threadId.htm"

    private fun record(name: String, call: suspend (FutabaRequestRecorder) -> Any?): String = runBlocking {
        val recorder = FutabaRequestRecorder { request ->
            if (request.url.host.endsWith(".2chan.net") && request.url.encodedPath.contains("/res/")) {
                respond("not found", HttpStatusCode.NotFound)
            } else futabaFixtureResponse(request)
        }
        val outcome = try {
            "ok:" + call(recorder)?.let { it::class.simpleName }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            "error:${failure::class.simpleName}"
        } finally {
            recorder.close()
        }
        // Only the request line (method + URL) and headers; none of these GETs has a body.
        (listOf("## $name -> $outcome") + recorder.lines).joinToString("\n")
    }

    private fun repository(recorder: FutabaRequestRecorder) =
        createRemoteBoardRepository(recorder.client, CookieRepository(recorder.cookieStorage))

    internal fun actual(): List<String> = listOf(
        record("performThreadArchiveFallback") {
            performThreadArchiveFallback(it.client, repository(it), threadId, null, board, null, Json)
        },
        record("futaberArchivedThread") {
            futaberArchivedThread(it.client, repository(it), threadId, board, Json)
        },
        record("tryRefreshHistoryEntryFromArchive") {
            tryRefreshHistoryEntryFromArchive(
                entry = ThreadHistoryEntry(threadId = threadId, boardId = "may-b", title = "スレ", titleImageUrl = "",
                    boardName = "二次元裏", boardUrl = threadUrl, lastVisitedEpochMillis = 0L, replyCount = 1),
                board = BoardSummary("may-b", "二次元裏", "", board, ""),
                httpClient = it.client,
                repository = repository(it),
                fetchTimeoutMillis = 30_000L,
                archiveSearchJson = Json,
                tag = "AM-B0"
            )
        },
        record("loadCompatThreadWithFallback") { recorder ->
            val repository = repository(recorder)
            loadCompatThreadWithFallback(
                sourceUrl = threadUrl,
                cacheEnabled = false,
                cacheBaseUrl = null,
                loader = repository::getThreadByUrl,
                expectedReplyCount = 1,
                archiveLoader = { archiveUrl -> fetchCompatArchiveThreadPage(recorder.client, archiveUrl) }
            ).exceptionOrNull()
        },
    )

    @Test
    fun theFiveArchiveFallbacksSendTheGoldenRequests() {
        val actual = actual()
        assertEquals(FUTABA_ARCHIVE_FALLBACK_GOLDEN.size, actual.size, actual.joinToString("\n\n"))
        FUTABA_ARCHIVE_FALLBACK_GOLDEN.zip(actual).forEach { (golden, got) -> assertEquals(golden, got) }
    }
}
