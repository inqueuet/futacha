package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.ui.futaber.futaberDefaultBoardName
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** H10: like the original, a board whose page cannot be read is not added. */
class FtutterBoardAddFetchTest {
    private fun clientAnswering(status: HttpStatusCode, body: String = "") =
        HttpClient(MockEngine { _ -> respond(body, status) })

    @Test
    fun aBoardThatAnswers404GivesNoName() = runBlocking {
        val client = clientAnswering(HttpStatusCode.NotFound, "<html>404</html>")
        try {
            assertNull(fetchFtutterBoardName(client, "https://may.2chan.net/zz/futaba.php"))
        } finally {
            client.close()
        }
    }

    @Test
    fun aFailedConnectionGivesNoName() = runBlocking {
        val client = HttpClient(MockEngine { _ -> throw IllegalStateException("offline") })
        try {
            assertNull(fetchFtutterBoardName(client, "https://may.2chan.net/27/futaba.php"))
        } finally {
            client.close()
        }
    }

    @Test
    fun aReadablePageGivesItsTitle() = runBlocking {
        val client = clientAnswering(HttpStatusCode.OK, """<span id="tit">ねこ＠ふたば</span>""")
        try {
            assertEquals("ねこ", fetchFtutterBoardName(client, "https://may.2chan.net/27/futaba.php"))
        } finally {
            client.close()
        }
    }

    @Test
    fun withoutAClientTheNameComesFromTheUrl() = runBlocking {
        assertEquals(
            futaberDefaultBoardName("https://may.2chan.net/27/futaba.php"),
            fetchFtutterBoardName(null, "https://may.2chan.net/27/futaba.php")
        )
    }
}
