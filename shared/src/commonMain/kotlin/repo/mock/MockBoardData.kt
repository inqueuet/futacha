package com.valoser.futacha.shared.repo.mock

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage

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
internal object MockBoardData {
    private const val HOST = "https://dat.2chan.net"
    private const val BOARD_PATH = "/t"
    private const val BOARD_BASE_URL = "$HOST$BOARD_PATH"
    private const val THREAD_BASE_URL = "$BOARD_BASE_URL/res"
    private const val ASSET_BASE_URL = "file:///android_asset/fixtures"

    val catalogItems: List<CatalogItem> = listOf(
        CatalogItem(
            id = "1762447153292",
            threadUrl = "$THREAD_BASE_URL/1762447153292.htm",
            title = "テスト1",
            thumbnailUrl = "$ASSET_BASE_URL/mock_catalog_thumb_1.png",
            replyCount = 1000
        ),
        CatalogItem(
            id = "1762447191833",
            threadUrl = "$THREAD_BASE_URL/1762447191833.htm",
            title = "テスト2",
            thumbnailUrl = "$ASSET_BASE_URL/mock_catalog_thumb_2.png",
            replyCount = 980
        ),
        CatalogItem(
            id = "1762447227319",
            threadUrl = "$THREAD_BASE_URL/1762447227319.htm",
            title = "テスト3",
            thumbnailUrl = "$ASSET_BASE_URL/mock_catalog_thumb_3.png",
            replyCount = 764
        ),
        CatalogItem(
            id = "1762447385737",
            threadUrl = "$THREAD_BASE_URL/1762447385737.htm",
            title = "テスト4",
            thumbnailUrl = "$ASSET_BASE_URL/mock_catalog_thumb_4.png",
            replyCount = 632
        ),
        CatalogItem(
            id = "1762447404525",
            threadUrl = "$THREAD_BASE_URL/1762447404525.htm",
            title = "テスト5",
            thumbnailUrl = "$ASSET_BASE_URL/mock_catalog_thumb_1.png",
            replyCount = 620
        ),
        CatalogItem(
            id = "1762447579073",
            threadUrl = "$THREAD_BASE_URL/1762447579073.htm",
            title = "テスト6",
            thumbnailUrl = "$ASSET_BASE_URL/mock_catalog_thumb_2.png",
            replyCount = 578
        ),
        CatalogItem(
            id = "1762447742611",
            threadUrl = "$THREAD_BASE_URL/1762447742611.htm",
            title = "テスト7",
            thumbnailUrl = "$ASSET_BASE_URL/mock_catalog_thumb_3.png",
            replyCount = 544
        ),
        CatalogItem(
            id = "1762447945405",
            threadUrl = "$THREAD_BASE_URL/1762447945405.htm",
            title = "テスト8",
            thumbnailUrl = "$ASSET_BASE_URL/mock_catalog_thumb_4.png",
            replyCount = 512
        ),
        CatalogItem(
            id = "1762447961108",
            threadUrl = "$THREAD_BASE_URL/1762447961108.htm",
            title = "テスト9",
            thumbnailUrl = "$ASSET_BASE_URL/mock_catalog_thumb_1.png",
            replyCount = 447
        ),
        CatalogItem(
            id = "1762448115097",
            threadUrl = "$THREAD_BASE_URL/1762448115097.htm",
            title = "テスト10",
            thumbnailUrl = "$ASSET_BASE_URL/mock_catalog_thumb_2.png",
            replyCount = 398
        ),
        CatalogItem(
            id = "1762448337733",
            threadUrl = "$THREAD_BASE_URL/1762448337733.htm",
            title = "テスト11",
            thumbnailUrl = "$ASSET_BASE_URL/mock_catalog_thumb_3.png",
            replyCount = 355
        ),
        CatalogItem(
            id = "1762448450095",
            threadUrl = "$THREAD_BASE_URL/1762448450095.htm",
            title = "テスト12",
            thumbnailUrl = "$ASSET_BASE_URL/mock_catalog_thumb_4.png",
            replyCount = 334
        ),
        CatalogItem(
            id = "1762448577081",
            threadUrl = "$THREAD_BASE_URL/1762448577081.htm",
            title = "テスト13",
            thumbnailUrl = "$ASSET_BASE_URL/mock_catalog_thumb_1.png",
            replyCount = 289
        ),
        CatalogItem(
            id = "1762448688021",
            threadUrl = "$THREAD_BASE_URL/1762448688021.htm",
            title = "テスト14",
            thumbnailUrl = "$ASSET_BASE_URL/mock_catalog_thumb_2.png",
            replyCount = 182
        ),
        CatalogItem(
            id = "1762448891200",
            threadUrl = "$THREAD_BASE_URL/1762448891200.htm",
            title = "テスト15",
            thumbnailUrl = "$ASSET_BASE_URL/mock_catalog_thumb_3.png",
            replyCount = 128
        ),
        CatalogItem(
            id = "1762449120001",
            threadUrl = "$THREAD_BASE_URL/1762449120001.htm",
            title = "テスト16",
            thumbnailUrl = "$ASSET_BASE_URL/mock_catalog_thumb_4.png",
            replyCount = 64
        )
    )

    private val threadPages: Map<String, ThreadPage> = mapOf(
        "354621" to ThreadPage(
            threadId = "354621",
            posts = listOf(
                Post(
                    id = "354621",
                    author = "Mock太郎",
                    subject = "Compose試作メモ",
                    timestamp = "24/11/03(日)13:47:04 IP:10.0.*.*",
                    messageHtml = "Compose 版クライアントの要件まとめ。<br>1) カタログの #cattable td をすべて拾う<br>2) 画像リンクは <code>/t/src/</code> と <code>/t/thumb/</code> を紐付ける<br>3) Shift_JIS を KMP 側で UTF-8 に揃える",
                    imageUrl = "$ASSET_BASE_URL/mock_catalog_header.png",
                    thumbnailUrl = "$ASSET_BASE_URL/mock_catalog_header.png"
                ),
                Post(
                    id = "354622",
                    author = "PreviewBot",
                    subject = "mock-thread",
                    timestamp = "24/11/03(日)13:47:57 IP:10.0.*.*",
                    messageHtml = "Compose プレビューでも HTML を再現したいので、blockquote には <code>&lt;br&gt;</code> を残したままにしておく。",
                    imageUrl = null,
                    thumbnailUrl = null
                ),
                Post(
                    id = "354623",
                    author = "名無しさん",
                    subject = "テスト観点",
                    timestamp = "24/11/03(日)13:56:40 IP:10.0.*.*",
                    messageHtml = "Mock では以下のケースを分岐に使う予定。<br>・画像付きレス<br>・本文のみレス<br>・削除済みレス（No.354629）<br>・そうだねボタン付きレス",
                    imageUrl = null,
                    thumbnailUrl = null
                ),
                Post(
                    id = "354624",
                    author = "Mock太郎",
                    subject = "del操作",
                    timestamp = "24/11/03(日)14:01:10 IP:10.0.*.*",
                    messageHtml = "del 依頼や本人削除は `ExampleBoardHttpSamples` のエントリを参照。UI では SnackBar で結果を通知する。",
                    imageUrl = null,
                    thumbnailUrl = null
                ),
                Post(
                    id = "354625",
                    author = "PreviewBot",
                    subject = "レスUI",
                    timestamp = "24/11/03(日)14:22:10 IP:10.0.*.*",
                    messageHtml = "レスカードでは subject → name → timestamp → No. の順に並べておくと HTML と対応が取りやすい。",
                    imageUrl = null,
                    thumbnailUrl = null
                ),
                Post(
                    id = "354626",
                    author = "Mock太郎",
                    subject = "画像レス",
                    timestamp = "24/11/03(日)14:53:15 IP:10.0.*.*",
                    messageHtml = "画像プレビュー用サンプル。",
                    imageUrl = "$ASSET_BASE_URL/mock_preview_image.jpg",
                    thumbnailUrl = "$ASSET_BASE_URL/mock_preview_image.jpg"
                ),
                Post(
                    id = "354627",
                    author = "PreviewBot",
                    subject = null,
                    timestamp = "24/11/03(日)15:19:55 IP:10.0.*.*",
                    messageHtml = ">画像プレビュー用サンプル<br>LazyColumn 側では aspectRatio を保つこと。",
                    imageUrl = null,
                    thumbnailUrl = null
                ),
                Post(
                    id = "354628",
                    author = "名無しさん",
                    subject = null,
                    timestamp = "24/11/03(日)15:58:37 IP:10.0.*.*",
                    messageHtml = "Mock とはいえ、レス番号は実データっぽい方が UI の検証には便利だね。",
                    imageUrl = null,
                    thumbnailUrl = null
                ),
                Post(
                    id = "354629",
                    author = "system",
                    subject = "削除済み",
                    timestamp = "24/11/03(日)16:17:58 IP:10.0.*.*",
                    messageHtml = "書き込みをした人によって削除されました",
                    imageUrl = null,
                    thumbnailUrl = null
                ),
                Post(
                    id = "354630",
                    author = "Mock太郎",
                    subject = null,
                    timestamp = "24/11/03(日)16:52:33 IP:10.0.*.*",
                    messageHtml = "ThreadViewModel から <code>BoardRepository</code> までのデータフローを確認中。",
                    imageUrl = null,
                    thumbnailUrl = null
                ),
                Post(
                    id = "354631",
                    author = "PreviewBot",
                    subject = null,
                    timestamp = "24/11/03(日)17:08:58 IP:10.0.*.*",
                    messageHtml = ">データフローを確認中<br>FakeBoardRepository を Hilt で差し替える Module も忘れずに。",
                    imageUrl = null,
                    thumbnailUrl = null
                ),
                Post(
                    id = "354632",
                    author = "Mock太郎",
                    subject = null,
                    timestamp = "24/11/03(日)17:37:43 IP:10.0.*.*",
                    messageHtml = "Parser 実装の TODO:<br>1) `.csb` → subject<br>2) `.cnm` → name<br>3) `.cnw` → timestamp<br>4) `.cno` → post number",
                    imageUrl = null,
                    thumbnailUrl = null
                ),
                Post(
                    id = "354634",
                    author = "PreviewBot",
                    subject = null,
                    timestamp = "24/11/03(日)18:59:05 IP:10.0.*.*",
                    messageHtml = "blockquote 内のテキストは HTML のまま保持し、UI で `HtmlCompat` などに任せる。",
                    imageUrl = null,
                    thumbnailUrl = null
                ),
                Post(
                    id = "354636",
                    author = "名無しさん",
                    subject = null,
                    timestamp = "24/11/03(日)19:10:08 IP:10.0.*.*",
                    messageHtml = "モックの本文は意図的に短めに調整しているので、レイアウトが暴れる場合は assets の fixture を見る。",
                    imageUrl = null,
                    thumbnailUrl = null
                ),
                Post(
                    id = "354652",
                    author = "Mock太郎",
                    subject = null,
                    timestamp = "24/11/04(月)17:53:15 IP:10.0.*.*",
                    messageHtml = "画像その2。実データに倣い PNG を指定。",
                    imageUrl = "$ASSET_BASE_URL/mock_catalog_badge.png",
                    thumbnailUrl = "$ASSET_BASE_URL/mock_catalog_badge.png"
                ),
                Post(
                    id = "354655",
                    author = "PreviewBot",
                    subject = null,
                    timestamp = "24/11/04(月)23:56:20 IP:10.0.*.*",
                    messageHtml = "リンク付きテキストの例: <a href=\"https://example.com\">mock link</a>",
                    imageUrl = null,
                    thumbnailUrl = null
                ),
                Post(
                    id = "354677",
                    author = "名無しさん",
                    subject = null,
                    timestamp = "24/11/06(水)07:53:21 IP:10.0.*.*",
                    messageHtml = "Thanks!",
                    imageUrl = null,
                    thumbnailUrl = null
                ),
                Post(
                    id = "354714",
                    author = "PreviewBot",
                    subject = "まとめ",
                    timestamp = "24/11/07(木)08:22:30 IP:10.0.*.*",
                    messageHtml = "レス終端。ここでは thumbnail + image を再度セットし、一覧側での差分を確認する。",
                    imageUrl = "$ASSET_BASE_URL/mock_summary.png",
                    thumbnailUrl = "$ASSET_BASE_URL/mock_summary.png"
                )
            )
        )
    )

    fun thread(threadId: String): ThreadPage {
        return threadPages[threadId] ?: ThreadPage(threadId = threadId, posts = emptyList())
    }
}
