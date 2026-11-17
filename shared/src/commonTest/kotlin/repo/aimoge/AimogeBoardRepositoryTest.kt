package com.valoser.futacha.shared.repo.aimoge

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.numericId
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.network.aimoge.AIMOGE_API_VERSION
import com.valoser.futacha.shared.network.aimoge.AIMOGE_UPDATE_REQUIRED_MESSAGE
import com.valoser.futacha.shared.network.aimoge.aimogeApiException
import com.valoser.futacha.shared.network.aimoge.aimogeBodyText
import com.valoser.futacha.shared.network.aimoge.aimogeErrorText
import com.valoser.futacha.shared.network.aimoge.aimogeThreadTitle
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val BOARD = "https://nijiurachan.net/b/ai2/"
private const val THREAD = "01a1ab6e-57e8-755b-a01c-66792ff13aef"
private val NOW = Instant.parse("2026-11-11T00:00:00Z").toEpochMilliseconds()

class AimogeBoardRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun repo(status: HttpStatusCode = HttpStatusCode.OK, body: (String) -> String): AimogeBoardRepository {
        val engine = MockEngine { request ->
            requests += request
            respond(body(request.url.encodedPath), status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return AimogeBoardRepository(HttpClient(engine), nowMillis = { NOW })
    }

    @Test
    fun theCatalogBecomesItemsWithTheUuidTheFirstLineAndTheOrder() = runTest {
        val items = repo { AimogeFixtures.CATALOG }.getCatalog(BOARD, CatalogMode.Many)
        val first = items.first()
        assertEquals(THREAD, first.id)
        assertEquals("https://nijiurachan.net/b/ai2/thread/$THREAD", first.threadUrl)
        assertEquals("[R18] museスレ", first.title)
        assertEquals(639, first.replyCount)
        assertEquals(100493594L, first.siteOrder)
        assertEquals(100493594L, first.numericId())
        assertTrue(first.thumbnailUrl!!.startsWith("https://storage.nijiurachan.net/"))
        val request = requests.single()
        assertEquals("api.nijiurachan.net", request.url.host)
        assertEquals("/catalog/ai2", request.url.encodedPath)
        assertEquals("replies", request.url.parameters["sort"])
        assertEquals(AIMOGE_API_VERSION, request.headers["X-Api-Version"])
        assertNull(request.headers[HttpHeaders.Cookie])
    }

    @Test
    fun eachTabAsksForItsSortAndFewIsSortedHere() = runTest {
        val r = repo { AimogeFixtures.CATALOG }
        mapOf(
            CatalogMode.Catalog to "bump", CatalogMode.New to "new", CatalogMode.Old to "old",
            CatalogMode.Momentum to "momentum", CatalogMode.So to "soudane"
        ).forEach { (mode, sort) ->
            r.getCatalog(BOARD, mode)
            assertEquals(sort, requests.last().url.parameters["sort"])
        }
        val few = r.getCatalog(BOARD, CatalogMode.Few)
        assertEquals(few.map { it.replyCount }.sorted(), few.map { it.replyCount })
    }

    @Test
    fun theThreadBecomesFutabaShapedPosts() = runTest {
        val page = repo { AimogeFixtures.THREAD }.getThread(BOARD, THREAD)
        assertEquals(THREAD, page.threadId)
        assertFalse(page.archived)
        assertFalse(page.isTruncated)
        val op = page.posts.first()
        assertEquals("100493594", op.id)
        assertEquals("01a1ab6e-57e8-755a-8c24-61da88f0bd4d", op.siteUuid)
        assertEquals("museスレ<br>もちもち", op.messageHtml)
        assertEquals("そうだねx12", op.saidaneLabel)
        assertTrue(op.timestamp.startsWith("26/11/05(木)20:18:17"), op.timestamp)
        assertEquals(page.posts.map { it.order }, page.posts.indices.toList())
        assertTrue(page.posts.any { it.attachments.size == 3 }, "a post with three pictures keeps all three (the readers draw the 2nd and 3rd)")
        // Before (2026-11-12 report): only the extras were kept, and every mode drew one picture fewer (3 → 2, 2 → 1).
        val three = page.posts.first { it.attachments.size == 3 }
        assertEquals(2, com.valoser.futacha.shared.service.threadSaveExtraAttachments(three).size)
        assertEquals(three.imageUrl, three.attachments.first().url)
        assertTrue(page.posts.any { "<font color=\"#789922\">&gt;" in it.messageHtml })
        assertTrue(page.posts.any { it.quoteReferences.isNotEmpty() }, "text quotes are resolved")
        assertEquals("/thread/$THREAD", requests.single().url.encodedPath)
    }

    @Test
    fun aClosedThreadIsNotFallen() = runTest {
        val page = repo { AimogeFixtures.THREAD_CLOSED }.getThread(BOARD, THREAD)
        assertFalse(page.archived)
        assertTrue(page.closedAtEpochMillis != null)
        assertEquals("このスレは閉じられています", page.expiresAtLabel)
    }

    @Test
    fun aThreadPastItsExpiryComesBackArchivedWithItsPosts() = runTest {
        val later = Instant.parse("2026-11-20T00:00:00Z").toEpochMilliseconds()
        val engine = MockEngine { respond(AimogeFixtures.THREAD, HttpStatusCode.OK) }
        val page = AimogeBoardRepository(HttpClient(engine), nowMillis = { later }).getThread(BOARD, THREAD)
        assertTrue(page.archived)
        assertTrue(page.posts.isNotEmpty())
        assertEquals(null, page.expiresAtLabel, "a fallen thread says no end time")
    }

    @Test
    fun errorsKeepTheStatusAndTheCode() = runTest {
        val e = assertFailsWith<NetworkException> {
            repo(HttpStatusCode.BadRequest) { AimogeFixtures.ERROR_SUCCESS_FALSE }.getThread(BOARD, THREAD)
        }
        assertEquals(400, e.statusCode)
        assertTrue(e.aimogeApiException() != null)
        val html = assertFailsWith<NetworkException> {
            repo(HttpStatusCode.BadGateway) { AimogeFixtures.ERROR_PLAIN_HTML }.getThread(BOARD, THREAD)
        }
        assertEquals(502, html.statusCode)
        assertNull(html.serverBody)
    }

    @Test
    fun theWebClientsTextsAreChosen() {
        assertEquals(AIMOGE_UPDATE_REQUIRED_MESSAGE, aimogeErrorText("UNSUPPORTED_API_VERSION", 400, "x"))
        assertEquals(
            "8文字以上の削除キーで書き込み、アカウントに紐づけてください。削除キーを変更した場合は、書き込み時の削除キーに戻してください",
            aimogeErrorText("ACCOUNT_REQUIRED", 403, "Delete key must be linked to an account")
        )
        assertEquals("入力内容を確認してください", aimogeErrorText(null, 400, "Bad Request"))
        assertEquals("この画像は現在のR18設定では投稿できません。画像を変更してお試しください", aimogeErrorText("NSFW_REJECTED", 422, "HTTP 422"))
        assertEquals("R18不可の板です。画像を変更してお試しください", aimogeErrorText("NSFW_REJECTED", 422, "R18不可の板です"))
        assertEquals("このスレッドのNGワードに一致するため投稿できません", aimogeErrorText("THREAD_NG_REJECTED", 422, "Forbidden"))
        assertNull(aimogeErrorText(null, 404, "Not Found"))
        assertNull(aimogeErrorText(null, 500, "HTTP 500"))
        assertEquals("混雑中", aimogeErrorText(null, 503, "混雑中"))
    }

    @Test
    fun privateUseMarkupIsTakenApart() {
        val dice = "a1d6=4b"
        assertEquals("a1d6=4b", aimogeBodyText(dice).shown)
        val hidden = "見える伏せ字"
        val text = aimogeBodyText(hidden)
        assertEquals("見える伏せ字", text.plain)
        assertEquals("見える字", text.spoken)
        val broken = "壊れ"
        assertEquals("壊れ", aimogeBodyText(broken).shown)
        assertEquals("最初の行", aimogeThreadTitle("\n 最初の行 \n次"))
    }

    @Test
    fun writesAreRefusedWithoutSending() = runTest {
        val r = repo { "{}" }
        // あいもげ is off in this test: a write is refused before anything is read or sent.
        val e = assertFailsWith<NetworkException> { r.voteSaidane(BOARD, THREAD, "1") }
        assertEquals(com.valoser.futacha.shared.network.aimoge.AIMOGE_SITES_OFF_MESSAGE, e.message)
        assertFailsWith<NetworkException> {
            r.replyToThread(BOARD, THREAD, "", "", "", "本文", "key", null, null, true)
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun theBoardsAreListed() = runTest {
        val boards = repo { AimogeFixtures.BOARDS }.boards()
        assertEquals("ai2", boards.first().slug)
        assertEquals("二次元AI裏", boards.first().name)
        assertEquals("https://nijiurachan.net/b/ai2/", boards.first().url)
    }

    @Test
    fun theCatalogPageCarriesTheCreatedTimesByThreadId() = runTest {
        val page = repo { AimogeFixtures.CATALOG }.getCatalogPage(BOARD, CatalogMode.Catalog)
        assertTrue(page.items.isNotEmpty())
        assertTrue(page.threadCreatedAtEpochMillis.isNotEmpty())
        assertTrue(page.threadCreatedAtEpochMillis.keys.all { key -> page.items.any { it.id == key } })
    }
}
