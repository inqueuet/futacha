package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.CompatQuoteIndex
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.compatQuoteQueryForPostLine
import com.valoser.futacha.shared.compat.shouldPreferLocalCompatSnapshot
import com.valoser.futacha.shared.compat.toCompatPlainText
import com.valoser.futacha.shared.compat.toCompatPostSnapshot
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.parser.CatalogHtmlParserCore
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore
import com.valoser.futacha.shared.repo.createRemoteBoardRepository
import com.valoser.futacha.shared.repo.mock.exampleCatalogHtml
import com.valoser.futacha.shared.repo.mock.exampleThreadHtml
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.ui.board.ThreadLoadExecutionResult
import com.valoser.futacha.shared.ui.board.reconcileArchiveLoadWithVisiblePage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

/** AM-B0 shared inputs and deterministic dumps for the Futaba golden tests in this package. */
internal object FutabaGoldenDumps {
    const val BOARD_URL = "https://may.2chan.net/b/futaba.php"

    /** A representative Futaba catalog table (cattable cells: title, png, protocol-relative, no image, big count). */
    val catalogHtml = """
        <html><body>
        <table border=1 align=center id='cattable'><tr>
        <td><a href='res/1439323740.htm' target='_blank'><img src='/b/cat/1762145224666s.jpg' border=0 width=50 height=38 alt=""></a><br><small>スレ題その1</small><br><font size=2>17</font></td>
        <td><a href='res/1439323741.htm' target='_blank'><img src='/b/cat/1762145224667s.png' border=0 width=38 height=50 alt=""></a><br><small>&amp;記号&lt;入り&gt;</small><br><font size=2>0</font></td>
        <td><a href='res/1439323742.htm' target='_blank'><img src='//may.2chan.net/b/cat/1762145224668s.jpg?x=1' border=0 width=50 height=50 alt=""></a><br><font size=2>999</font></td>
        </tr><tr>
        <td><a href='res/1439323743.htm' target='_blank'></a><small>画像なし</small><br><font size=2>3</font></td>
        </tr></table>
        </body></html>
    """.trimIndent()

    suspend fun catalogDump(): String {
        val synthetic = CatalogHtmlParserCore.parseCatalog(catalogHtml, BOARD_URL)
        val example = CatalogHtmlParserCore.parseCatalog(exampleCatalogHtml, BOARD_URL)
        return withoutSiteDefaults((synthetic + example).joinToString("\n"))
    }

    suspend fun exampleThread(): ThreadPage =
        ThreadHtmlParserCore.parseThread(exampleThreadHtml, "https://may.2chan.net/b/res/1364612020.htm")

    suspend fun threadDump(): String {
        val page = exampleThread()
        return withoutSiteDefaults(buildString {
            append(page.copy(posts = emptyList())).append('\n')
            page.posts.forEach { append(it).append('\n') }
        })
    }

    /** Hand-made Futaba posts covering the quote forms: text, No., ID, file name, nested, unknown, number-lead. */
    val quotePosts: List<Post> = listOf(
        post("100", 0, "本文A<br>行B", posterId = "ID:aaa111", image = "https://may.2chan.net/b/src/1762000000111.jpg"),
        post("101", 1, "<font color=\"#789922\">&gt;本文A</font><br>返信1", posterId = "ID:bbb222"),
        post("102", 2, "<font color=\"#789922\">&gt;No.101</font><br>番号引用"),
        post("103", 3, "<font color=\"#789922\">&gt;ID:aaa111</font><br>ID引用"),
        post("104", 4, "<font color=\"#789922\">&gt;1762000000111.jpg</font><br>ファイル名引用"),
        post("105", 5, "<font color=\"#789922\">&gt;&gt;本文A</font><br><font color=\"#789922\">&gt;返信1</font><br>入れ子"),
        post("106", 6, "<font color=\"#789922\">&gt;存在しない行</font>"),
        post("107", 7, "<font color=\"#789922\">&gt;3時のおやつ</font><br>数字始まり"),
        post("108", 8, "行B<br><font color=\"#789922\">&gt;行B</font>")
    )

    private fun post(id: String, order: Int, html: String, posterId: String? = null, image: String? = null) = Post(
        id = id, order = order, author = "としあき", subject = "無念",
        timestamp = "26/11/10(月)12:00:0$order", posterId = posterId, messageHtml = html,
        imageUrl = image, thumbnailUrl = image?.replace("/src/", "/thumb/")?.replace(".jpg", "s.jpg")
    )

    fun referenceDump(posts: List<Post>): String =
        ThreadHtmlParserCore.rebuildReferences(posts.map { it.copy(quoteReferences = emptyList(), referencedCount = 0) })
            .joinToString("\n") { "${it.id} count=${it.referencedCount} parsed=${it.quoteReferencesParsed} refs=${it.quoteReferences}" }

    fun quoteIndexDump(posts: List<Post>): String {
        val snaps = posts.mapIndexed { i, p -> p.toCompatPostSnapshot(i) }
        val index = CompatQuoteIndex(snaps)
        return buildString {
            snaps.forEach { s ->
                s.messageHtml.toCompatPlainText().lines().forEach { line ->
                    val q = compatQuoteQueryForPostLine(s, line) ?: return@forEach
                    append("${s.postNo} [${q.replace("\n", "\\n")}] -> ${index.resolve(s.position, q).map { it.postNo }}")
                    append(" first=${index.resolveFirst(s.position, q)?.postNo}\n")
                }
            }
        }.trimEnd()
    }

    private fun snap(posts: List<Post>, truncated: Boolean = false) = CompatThreadSnapshot(
        tabKey = "compat_tab_x", revision = 1, fetchedAtEpochMillis = 1,
        posts = posts.mapIndexed { i, p -> p.toCompatPostSnapshot(i) }, isTruncated = truncated
    )

    fun freshnessDump(): String {
        val all = quotePosts
        val edited = all.take(5).mapIndexed { i, p -> if (i == 1) p.copy(messageHtml = "編集") else p }
        val deleted = all.take(5).mapIndexed { i, p -> if (i == 2) p.copy(isDeleted = true) else p }
        val cases = listOf(
            "noLocal" to (null to snap(all)),
            "emptyFetched" to (snap(all) to snap(emptyList())),
            "localLonger" to (snap(all) to snap(all.take(5))),
            "serverLonger" to (snap(all.take(5)) to snap(all)),
            "sameSize" to (snap(all.take(5)) to snap(edited)),
            "serverTruncatedSameSize" to (snap(all.take(5)) to snap(all.take(5), truncated = true)),
            "localTruncatedShorterServer" to (snap(all, truncated = true) to snap(all.take(5))),
            "serverDeletedFlagSameSize" to (snap(all.take(5)) to snap(deleted)),
            "notAPrefix" to (snap(all) to snap(all.drop(1).take(3))),
        )
        return cases.joinToString("\n") { (name, pair) -> "$name=${shouldPreferLocalCompatSnapshot(pair.first, pair.second)}" }
    }

    fun reconcileDump(): String {
        fun page(posts: List<Post>, truncated: Boolean = false) = ThreadPage(
            threadId = "100", boardTitle = null, expiresAtLabel = null, deletedNotice = null,
            posts = posts, isTruncated = truncated
        )
        val all = quotePosts
        val archive = page(all.take(4).map { it.copy(messageHtml = it.messageHtml + "[arc]") })
        val deletedArchive = page(all.take(9).mapIndexed { i, p -> if (i == 3) p.copy(isDeleted = true, messageHtml = "削除") else p })
        fun res(p: ThreadPage, fromArchive: Boolean = true, offline: Boolean = false) =
            ThreadLoadExecutionResult(page = p, usedOffline = offline, nextThreadUrlOverride = null, fromArchive = fromArchive)
        fun show(r: ThreadLoadExecutionResult) =
            "offline=${r.usedOffline} truncated=${r.page.isTruncated} posts=${r.page.posts.joinToString(",") { "${it.id}:${it.messageHtml.takeLast(5)}:${it.isDeleted}" }}"
        val cases = listOf(
            "notArchive" to reconcileArchiveLoadWithVisiblePage(res(archive, fromArchive = false), "100", page(all), true),
            "otherThread" to reconcileArchiveLoadWithVisiblePage(res(archive), "999", page(all), true),
            "localCopyWins" to reconcileArchiveLoadWithVisiblePage(res(archive), "100", page(all), true),
            "remoteFillsGaps" to reconcileArchiveLoadWithVisiblePage(res(archive), "100", page(all), false),
            "remoteCovered" to reconcileArchiveLoadWithVisiblePage(res(page(all)), "100", page(all.take(3)), false),
            "truncatedVisible" to reconcileArchiveLoadWithVisiblePage(res(archive), "100", page(all, truncated = true), false),
            "deletedInArchiveLocal" to reconcileArchiveLoadWithVisiblePage(res(deletedArchive), "100", page(all), true),
            "deletedInArchiveRemote" to reconcileArchiveLoadWithVisiblePage(res(deletedArchive), "100", page(all), false),
            "noVisible" to reconcileArchiveLoadWithVisiblePage(res(archive), "100", null, true),
        )
        return cases.joinToString("\n") { (name, r) -> "$name: ${show(r)}" }
    }

    /** Three windowed runs over five Futaba history rows; records requests, cursor file and fetched ids per run. */
    suspend fun historyDump(): String {
        val requests = mutableListOf<String>()
        val engine = MockEngine { request ->
            requests += "${request.method.value} ${request.url}"
            val path = request.url.encodedPath
            if ("/res/" in path) {
                val id = path.substringAfterLast('/').substringBefore('.')
                respond(
                    exampleThreadHtml.substringAfter("---\n").replace("1364612020", id),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
                )
            } else {
                respond("<html></html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()))
            }
        }
        val repository = createRemoteBoardRepository(HttpClient(engine))
        val board = BoardSummary(id = "b", name = "虹裏may", category = "", url = BOARD_URL, description = "")
        val entries = (1..5).map { n ->
            ThreadHistoryEntry(
                threadId = "143932374$n", boardId = "b", title = "t$n", titleImageUrl = "",
                boardName = "虹裏may", boardUrl = "https://may.2chan.net/b/res/143932374$n.htm",
                lastVisitedEpochMillis = n.toLong(), replyCount = 1
            )
        }
        val store = AppStateStore(FakePlatformStateStorage())
        store.setHistory(entries)
        val fs = InMemoryFileSystem()
        fun refresher() = HistoryRefresher(
            stateStore = store, repository = repository, dispatcher = Dispatchers.Default.limitedParallelism(1),
            fileSystem = fs, maxConcurrency = 1
        )
        val out = StringBuilder()
        suspend fun run(label: String, block: suspend () -> Unit) {
            requests.clear()
            block()
            val cursor = fs.readString("private/history_refresh_cursor.json").getOrNull()
            out.append("$label requests=${requests.size}\n")
            requests.forEach { out.append("  ").append(it).append('\n') }
            out.append("  cursor=$cursor\n")
        }
        run("run1 window2") { refresher().refresh(listOf(board), entries, maxThreadsPerRun = 2) }
        run("run2 window2 newInstance") { refresher().refresh(listOf(board), entries, maxThreadsPerRun = 2) }
        run("run3 window3 budget0") { refresher().refresh(listOf(board), entries, maxThreadsPerRun = 3, runBudgetMillis = 0) }
        run("run4 window2 afterBudget") { refresher().refresh(listOf(board), entries, maxThreadsPerRun = 2) }
        out.append("history=").append(store.history.first().joinToString(",") { "${it.threadId}:${it.replyCount}:${it.title}" })
        return out.toString()
    }

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).joinToString("") { "%02x".format(it) }
}
