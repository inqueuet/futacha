package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.repo.createRemoteBoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.ui.board.FutachaSharedBoardRepository
import com.valoser.futacha.shared.ui.compat.COMPAT_CACHE_AVAILABLE_KEY
import com.valoser.futacha.shared.ui.compat.COMPAT_CACHE_ENABLED_KEY
import com.valoser.futacha.shared.util.JvmFileSystem
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AM-B0 (docs/aimoge-support-plan-2026-11-10.md): the requests `FutachaSharedBoardRepository` sends for a Futaba
 * board's catalog, thread (by board+id and by URL, with the cache server off and on) and `supplement`. These must stay
 * byte for byte after あいもげ support. Uses the desktop compatibility store in a temporary folder (JVM only).
 */
class FutachaSharedBoardRepositoryRequestsTest {
    private val board = FIXTURE_BOARD
    private val threadUrl = "${board}res/$FIXTURE_THREAD_ID.htm"

    private fun record(
        name: String,
        preferences: Map<String, String> = emptyMap(),
        normalize: (List<String>) -> List<String> = { it },
        call: suspend (FutachaSharedBoardRepository) -> Any?
    ): String = runBlocking {
        val root = Files.createTempDirectory("am-b0-shared").toFile()
        // The cache server (may.inqueuet.com) and the archives answer 404, the board its fixtures.
        val recorder = FutabaRequestRecorder { request ->
            if (request.url.host.endsWith(".2chan.net")) futabaFixtureResponse(request)
            else respond("missing", HttpStatusCode.NotFound)
        }
        val outcome = try {
            val store = DesktopCompatibilityStore(JvmFileSystem(root))
            store.initialize()
            preferences.forEach { (key, value) -> store.savePreference(key, value) }
            val delegate = createRemoteBoardRepository(recorder.client, CookieRepository(recorder.cookieStorage))
            "ok:" + call(FutachaSharedBoardRepository(delegate, store, recorder.client))?.let { it::class.simpleName }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            "error:${failure::class.simpleName}:${failure.message}"
        } finally {
            recorder.close()
            root.deleteRecursively()
        }
        (listOf("## $name -> $outcome") + normalize(recorder.lines)).joinToString("\n")
    }

    private val cacheOn = mapOf(COMPAT_CACHE_ENABLED_KEY to "ON", COMPAT_CACHE_AVAILABLE_KEY to "ON")

    internal fun actual(): List<String> = listOf(
        record("getCatalogPage") { it.getCatalogPage(board, CatalogMode.Catalog) },
        record("getCatalog") { it.getCatalog(board, CatalogMode.New) },
        record("getThreadContent") { it.getThreadContent(board, FIXTURE_THREAD_ID) },
        record("getThreadContentByUrl") { it.getThreadContentByUrl(threadUrl) },
        record("getThreadContent+cache", cacheOn) { it.getThreadContent(board, FIXTURE_THREAD_ID) },
        record("getThreadContentByUrl+cache", cacheOn) { it.getThreadContentByUrl(threadUrl) },
        // supplement asks the archives two at a time in parallel (IO dispatcher): the pairs are fixed, the order inside
        // a pair is not, so each pair is sorted.
        record("supplement", normalize = { lines ->
            listOf(lines.first()) + lines.drop(1).chunked(2).flatMap { it.sorted() }
        }) { repository ->
            val content = repository.getThreadContentByUrl(threadUrl)
            // A truncated page asks every archive candidate for the missing posts.
            repository.supplement(threadUrl, content.copy(page = content.page.copy(isTruncated = true)))
        },
    )

    @Test
    fun catalogThreadAndSupplementSendTheGoldenRequests() {
        val actual = actual()
        assertEquals(FUTACHA_SHARED_REPOSITORY_GOLDEN.size, actual.size, actual.joinToString("\n\n"))
        FUTACHA_SHARED_REPOSITORY_GOLDEN.zip(actual).forEach { (golden, got) -> assertEquals(golden, got) }
    }
}
