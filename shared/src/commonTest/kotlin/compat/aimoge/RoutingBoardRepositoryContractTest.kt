package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.network.HttpConditionalValidators
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.createRemoteBoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AM-B0 (docs/aimoge-support-plan-2026-11-10.md): every [BoardRepository] method of the real Futaba repository
 * ([createRemoteBoardRepository] over HttpBoardApi), on https://may.2chan.net/b/, and the requests each one sends.
 * The routing repository of あいもげ support must send these requests byte for byte for a Futaba board.
 */
class RoutingBoardRepositoryContractTest {
    private val board = FIXTURE_BOARD
    private val thread = FIXTURE_THREAD_ID
    private val threadUrl = "${board}res/$thread.htm"

    /** One fresh repository and recorder per call, so each call's requests include its own cookie setup. */
    private fun record(name: String, call: suspend (BoardRepository) -> Any?): String = runBlocking {
        val recorder = FutabaRequestRecorder()
        val repository = createRemoteBoardRepository(recorder.client, CookieRepository(recorder.cookieStorage))
        val outcome = try {
            "ok:" + summarize(call(repository))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            "error:${failure::class.simpleName}:${failure.message}"
        } finally {
            recorder.close()
        }
        (listOf("## $name -> $outcome") + recorder.lines).joinToString("\n")
    }

    private fun summarize(value: Any?): String = when (value) {
        null, is Unit -> "-"
        is List<*> -> "list(${value.size})"
        is String -> value
        is Boolean -> value.toString()
        else -> value::class.simpleName.orEmpty()
    }

    private val item = CatalogItem(id = thread, threadUrl = threadUrl, title = null, thumbnailUrl = null, fullImageUrl = null, replyCount = 1)

    internal fun actual(): List<String> = listOf(
        record("getCatalogFetchSettings") { it.getCatalogFetchSettings() },
        record("getPostingCapabilities") { it.getPostingCapabilities(board, thread) },
        record("getCatalog") { it.getCatalog(board, CatalogMode.Catalog) },
        record("getCatalogWithSettings") { it.getCatalogWithSettings(board, CatalogMode.New, CatalogFetchSettings(columns = 12, rows = 25, titleLines = 256)) },
        record("getCatalogPageWithSettings") { it.getCatalogPageWithSettings(board, CatalogMode.Old, CatalogFetchSettings(rows = 60)) },
        record("getCatalogPage") { it.getCatalogPage(board, CatalogMode.Many) },
        record("fetchOpImageUrl") { it.fetchOpImageUrl(board, thread) },
        record("resolveCatalogDisplayTitle") { it.resolveCatalogDisplayTitle(board, item) },
        record("getThread") { it.getThread(board, thread) },
        record("getThreadContent") { it.getThreadContent(board, thread) },
        record("getThreadIfModified") { it.getThreadIfModified(board, thread, HttpConditionalValidators(etag = "\"e1\"", lastModified = "Mon, 10 Nov 2025 03:00:00 GMT")) },
        record("getThreadByUrl") { it.getThreadByUrl(threadUrl) },
        record("getThreadContentByUrl") { it.getThreadContentByUrl(threadUrl) },
        record("probeThreadExists") { it.probeThreadExists(threadUrl) },
        record("probeThreadGone") { it.probeThreadGone(threadUrl) },
        record("voteSaidane") { it.voteSaidane(board, thread, "1439323741") },
        record("voteSaidaneResponse") { it.voteSaidaneResponse(board, thread, "1439323741") },
        record("requestDeletion") { it.requestDeletion(board, thread, "1439323741", "110") },
        record("requestDeletionResponse") { it.requestDeletionResponse(board, thread, "1439323741", "110") },
        record("deleteByUser") { it.deleteByUser(board, thread, "1439323741", "delkey01", false) },
        record("deleteByUserResponse") { it.deleteByUserResponse(board, thread, "1439323741", "delkey01", true) },
        record("replyToThread") { it.replyToThread(board, thread, "名前", "sage", "題名", "本文です", "delkey01", null, null, true) },
        record("replyToThread+image") { it.replyToThread(board, thread, "", "", "", "画像", "delkey01", byteArrayOf(-1, -40, -1, -39), "a.jpg", false) },
        record("createThread") { it.createThread(board, "", "", "題", "新スレ", "delkey01", null, null, true) },
        record("clearOpImageCache") { it.clearOpImageCache(board, thread) },
        record("invalidateCookies") { it.invalidateCookies(board) },
        record("closeAsync") { it.closeAsync().join() },
        record("close") { it.close() },
    )

    @Test
    fun everyMethodSendsTheGoldenRequests() {
        val actual = actual()
        assertEquals(ROUTING_BOARD_REPOSITORY_GOLDEN.size, actual.size, actual.joinToString("\n\n"))
        ROUTING_BOARD_REPOSITORY_GOLDEN.zip(actual).forEach { (golden, got) -> assertEquals(sortedCookies(golden), got) }
    }

    /** The golden text with each Cookie header's cookies sorted, as the recorder writes them. */
    private fun sortedCookies(text: String): String =
        Regex("Cookie=([^\\]]*?)(?=\\]|, [A-Z][A-Za-z-]*=)").replace(text) { m ->
            "Cookie=" + m.groupValues[1].split("; ").sorted().joinToString("; ")
        }
}
