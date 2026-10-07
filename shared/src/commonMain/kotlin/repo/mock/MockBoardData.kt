package com.valoser.futacha.shared.repo.mock

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.parser.CatalogHtmlParserCore
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

/**
 * Mock snapshot backed by the Futaba HTML/API captures checked into `/example`.
 *
 * - Catalog entries mirror `example/catalog.txt` (Shift_JIS catalog dump)
 * - Thread content mirrors `example/thread.txt`
 * - ローカル画像は `app-android/src/main/assets/fixtures/` 配下を `file:///android_asset/fixtures/...` で参照
 *
 * Keeping these values in sync with the captured sources allows Compose previews, tests, and Hilt
 * fakes to reflect the markup documented in codex.md without hitting the real network.
 */
@OptIn(ExperimentalTime::class)
internal object MockBoardData {
    private val now = Clock.System.now()
    private val scope = CoroutineScope(SupervisorJob() + AppDispatchers.mockData)

    private val catalogItemsDeferred = scope.async(start = CoroutineStart.LAZY) {
        CatalogHtmlParserCore.parseCatalog(exampleCatalogHtml).mapIndexed { index, item ->
            when (index) {
                0 -> item.copy(expiresAtEpochMillis = (now + 10.minutes).toEpochMilliseconds())
                3 -> item.copy(expiresAtEpochMillis = (now + 3.hours).toEpochMilliseconds())
                else -> item
            }
        }
    }

    suspend fun catalogItems(): List<CatalogItem> = catalogItemsDeferred.await()

    private val baseThreadPageDeferred = scope.async(start = CoroutineStart.LAZY) {
        val page = ThreadHtmlParserCore.parseThread(exampleThreadHtml)
        page.copy(posts = page.posts.mapIndexed { index, post ->
            if (index != 0) post else post.copy(messageHtml = """
                チュートリアル<br>
                ここは操作を試すサンプル板です。画像はアプリに同梱した見本です。<br><br>
                板一覧の追加メニューから、見たい板のURLを登録してください。<br>
                カタログのグリッド表示は格子状、リスト表示は縦一列です。<br><br>
                ふたちゃ：設定 → スレ表示モードで通常／ツリー表示を選べます。<br>
                としあき（仮）：設定 → スレッド画面 → レスの表示形式で選べます。<br>
                設定 → 操作 → レスのタップ操作で、関連レスをタップで開く方式も選べます。
            """.trimIndent())
        })
    }
    private val threadPages: MutableMap<String, ThreadPage> = mutableMapOf(
        // Placeholder; actual entries filled after first parse
    )

    suspend fun thread(threadId: String): ThreadPage {
        val baseThreadPage = baseThreadPageDeferred.await()
        if (threadPages.isEmpty()) {
            threadPages[baseThreadPage.threadId] = baseThreadPage
        }
        if (threadId.isBlank()) return baseThreadPage
        return threadPages.getOrPut(threadId) {
            baseThreadPage.copy(
                threadId = threadId,
                boardTitle = null
            )
        }
    }
}
