package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.normalizeArchiveReportThreadUrl
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.network.aimoge.AimogeApiException
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.RoutingBoardRepository
import com.valoser.futacha.shared.repo.aimoge.AIMOGE_APPROVAL_NEEDED_MESSAGE
import com.valoser.futacha.shared.repo.aimoge.AimogeBoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.repo.withAimogeRouting
import com.valoser.futacha.shared.ui.board.FutachaSharedBoardRepository
import com.valoser.futacha.shared.ui.board.shouldOfferCookieManagerForPostingFailure
import com.valoser.futacha.shared.ui.compat.COMPAT_CACHE_AVAILABLE_KEY
import com.valoser.futacha.shared.ui.compat.COMPAT_CACHE_ENABLED_KEY
import com.valoser.futacha.shared.ui.compat.buildCompatArchiveThreadCandidates
import com.valoser.futacha.shared.ui.image.archiveImageFallbackCandidates
import com.valoser.futacha.shared.util.JvmFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val UUID = "0193b2c4-1a2b-7c3d-8e4f-5a6b7c8d9e0f"
private const val A_BOARD = "https://nijiurachan.net/b/aimoge/"
private const val A_THREAD = "https://nijiurachan.net/b/aimoge/thread/$UUID"
private const val F_BOARD = "https://may.2chan.net/b/"

/** AM-B4: the routing, the Futaba-only paths shut for あいもげ, and the current write calls refused without a request. */
class RoutingBoardRepositoryAimogeTest {
    /** Counts the calls that reach it; everything else is the fake's. */
    private class Counting(private val inner: BoardRepository = FakeBoardRepository()) : BoardRepository by inner {
        val calls = mutableListOf<String>()
        override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent {
            calls += "thread $board"; return ThreadPageContent(ThreadPage(threadId, null, null, null, emptyList()))
        }
        override suspend fun getThreadContentByUrl(threadUrl: String): ThreadPageContent {
            calls += "url $threadUrl"; return ThreadPageContent(ThreadPage("t", null, null, null, emptyList()))
        }
        override suspend fun getCatalogPage(board: String, mode: CatalogMode) = inner.getCatalogPage(board, mode).also { calls += "catalog $board" }
        override suspend fun getCatalog(board: String, mode: CatalogMode) = inner.getCatalog(board, mode).also { calls += "catalogList $board" }
        override suspend fun getArchivedThreadContent(board: String, threadId: String): ThreadPageContent? { calls += "archive $board"; return null }
        override suspend fun probeThreadGone(threadUrl: String): Boolean { calls += "gone $threadUrl"; return false }
        var closed = 0
        override fun close() { closed++ }
    }

    @Test
    fun whileAimogeIsOffEverythingGoesToFutaba() = runBlocking {
        val futaba = Counting(); val aimoge = Counting()
        val routing = RoutingBoardRepository(futaba, aimoge)
        routing.getThreadContent(A_BOARD, UUID)
        routing.getArchivedThreadContent(A_BOARD, UUID)
        assertEquals(2, futaba.calls.size)
        assertTrue(aimoge.calls.isEmpty())
    }

    @Test
    fun eachSiteGoesToItsOwnRepository() = withAimoge {
        runBlocking {
            val futaba = Counting(); val aimoge = Counting()
            val routing = RoutingBoardRepository(futaba, aimoge)
            routing.getThreadContent(A_BOARD, UUID)
            routing.getThreadContentByUrl(A_THREAD)
            routing.getArchivedThreadContent(A_BOARD, UUID)
            routing.probeThreadGone(A_THREAD)
            routing.getThreadContent(F_BOARD, "1")
            routing.getArchivedThreadContent(F_BOARD, "1")
            assertEquals(listOf("thread $A_BOARD", "url $A_THREAD", "archive $A_BOARD", "gone $A_THREAD"), aimoge.calls)
            assertEquals(listOf("thread $F_BOARD", "archive $F_BOARD"), futaba.calls)
            routing.close()
            assertEquals(1, futaba.closed); assertEquals(1, aimoge.closed)
        }
    }

    @Test
    fun theAimogeClientIsOnlyMadeForAnAimogeRequest() = withAimoge {
        runBlocking {
            var made = 0
            val routing = withAimogeRouting(Counting()) { made++; HttpClient(MockEngine { respond("{}", HttpStatusCode.NotFound) }) }
            routing.getThreadContent(F_BOARD, "1")
            routing.close()
            assertEquals(0, made)
        }
    }

    /** The current write calls (the 26 call sites of the plan go through these 8): refused, nothing sent, also via routing. */
    @Test
    fun writeCallsSendNothingForAimogeBeforeApproval() = withAimoge {
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val client = HttpClient(MockEngine { requests += it; respond("{}", HttpStatusCode.OK) })
            val routing = RoutingBoardRepository(Counting(), AimogeBoardRepository(client))
            val writes: List<Pair<String, suspend () -> Any?>> = listOf(
                "replyToThread" to { routing.replyToThread(A_BOARD, UUID, "", "", "", "本文", "", null, null, true) },
                "createThread" to { routing.createThread(A_BOARD, "", "", "", "本文", "", null, null, true) },
                "voteSaidane" to { routing.voteSaidane(A_BOARD, UUID, "1") },
                "requestDeletion" to { routing.requestDeletion(A_BOARD, UUID, "1", "110") },
                "deleteByUser" to { routing.deleteByUser(A_BOARD, UUID, "1", "key", false) },
                "voteSaidaneResponse" to { routing.voteSaidaneResponse(A_BOARD, UUID, "1") },
                "requestDeletionResponse" to { routing.requestDeletionResponse(A_BOARD, UUID, "1", "110") },
                "deleteByUserResponse" to { routing.deleteByUserResponse(A_BOARD, UUID, "1", "key", false) },
            )
            for ((name, write) in writes) {
                val failure = assertFailsWith<NetworkException>(name) { write() }
                // Not approved (AM-B9): the approval is asked for and nothing is read or sent.
                assertEquals(AIMOGE_APPROVAL_NEEDED_MESSAGE, failure.message, name)
            }
            assertTrue(requests.isEmpty(), requests.joinToString { it.url.toString() })
            client.close()
        }
    }

    @Test
    fun theSharedRepositoryPassesAimogeStraightThrough() = withAimoge {
        runBlocking {
            val root = Files.createTempDirectory("am-b4-shared").toFile()
            val requests = mutableListOf<HttpRequestData>()
            val client = HttpClient(MockEngine { requests += it; respond("missing", HttpStatusCode.NotFound) })
            try {
                val store = DesktopCompatibilityStore(JvmFileSystem(root))
                store.initialize()
                store.savePreference(COMPAT_CACHE_ENABLED_KEY, "ON")
                store.savePreference(COMPAT_CACHE_AVAILABLE_KEY, "ON")
                val delegate = Counting()
                val shared = FutachaSharedBoardRepository(delegate, store, client)
                shared.getThreadContent(A_BOARD, UUID)
                val content = shared.getThreadContentByUrl(A_THREAD)
                shared.getCatalogPage(A_BOARD, CatalogMode.Catalog)
                shared.getCatalog(A_BOARD, CatalogMode.Catalog)
                // A truncated page would ask the Futaba archives: not for あいもげ.
                shared.supplement(A_THREAD, content.copy(page = content.page.copy(isTruncated = true)))
                assertEquals(
                    listOf("thread $A_BOARD", "url $A_THREAD", "catalog $A_BOARD", "catalogList $A_BOARD"),
                    delegate.calls
                )
                assertTrue(requests.isEmpty(), requests.joinToString { it.url.toString() })
            } finally {
                client.close()
                root.deleteRecursively()
            }
        }
    }

    @Test
    fun anAimogeFailureNeverOffersTheCookieScreen() {
        val words = "posttime cookie ipアドレス 期限"
        assertTrue(shouldOfferCookieManagerForPostingFailure(NetworkException(words)))
        assertFalse(shouldOfferCookieManagerForPostingFailure(NetworkException(words, cause = AimogeApiException("x", 403, words, null))))
        assertFalse(withAimoge { shouldOfferCookieManagerForPostingFailure(NetworkException(words), boardUrl = A_BOARD) })
        assertTrue(shouldOfferCookieManagerForPostingFailure(NetworkException(words), boardUrl = F_BOARD))
    }

    @Test
    fun theFutabaArchivesNeverSeeAnAimogeAddress() {
        assertTrue(buildCompatArchiveThreadCandidates(A_THREAD).isEmpty())
        assertTrue(buildCompatArchiveThreadCandidates("https://may.2chan.net/b/res/1.htm").isNotEmpty())
        assertNull(normalizeArchiveReportThreadUrl(A_THREAD))
        assertTrue(archiveImageFallbackCandidates("https://nijiurachan.net/b/aimoge/src/1.jpg").isEmpty())
    }
}
