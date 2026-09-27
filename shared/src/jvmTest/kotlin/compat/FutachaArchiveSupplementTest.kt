package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.util.JvmFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class FutachaArchiveSupplementTest {
    @Test fun stalledFirstPairStillAllowsForestToFillMissingReplies() = runBlocking {
        val root = Files.createTempDirectory("futacha-archive-budget").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        val source = "https://may.2chan.net/b/res/123.htm"
        val op = """<span class="cno">No.123</span><blockquote>OP</blockquote>"""
        val replies = """<table border=0><tr><td><span class="cno">No.124</span><blockquote>reply</blockquote></td></tr></table>"""
        val client = HttpClient(MockEngine { request ->
            if (request.url.host != "futabaforest.net") awaitCancellation()
            respond("<script>\$data = `$op$replies`;</script>", headers = headersOf("Content-Type", "text/html; charset=UTF-8"))
        })
        try {
            store.initialize()
            val partial = ThreadHtmlParserCore.parseThread("""<div class="thre">$op</div>""").copy(isTruncated = true)
            val repository = FutachaSharedBoardRepository(FakeBoardRepository(), store, client)
            val result = repository.supplement(source, ThreadPageContent(partial))
            assertEquals(listOf("123", "124"), result.page.posts.map { it.id })
            assertFalse(result.page.isTruncated)
        } finally { client.close(); store.close(); root.deleteRecursively() }
    }
}
