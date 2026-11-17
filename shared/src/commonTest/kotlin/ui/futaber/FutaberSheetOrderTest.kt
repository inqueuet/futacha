package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.buildCompatThreadNgRuleIndex
import com.valoser.futacha.shared.compat.matchesCompatThreadNg
import com.valoser.futacha.shared.compat.toCompatThreadSnapshot
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.ui.board.CanonicalNgRules
import com.valoser.futacha.shared.ui.compat.CompatImageSearchResult
import com.valoser.futacha.shared.ui.compat.CompatImageSearchTarget
import com.valoser.futacha.shared.ui.compat.DEFAULT_COMPAT_ASCII2D_ENDPOINT
import com.valoser.futacha.shared.ui.compat.compatCommonNgFilterRules
import com.valoser.futacha.shared.ui.ftutter.ftutterCommonNgPosts
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * L-B5「シートの並び」(docs/futaber-evolution-look-2026-10-31.md §2.3〜§2.7): every sheet lists the original's items first
 * in the original's order and this app's own items and the extensions after them, marked `extra`; the copy panel's
 * text; the IP's NG entry and how every mode matches it; the direct picture searches (a mock engine: nothing is sent).
 */
class FutaberSheetOrderTest {
    private fun post(
        id: String,
        body: String = "本文$id",
        timestamp: String = "26/10/16(金)22:37:55",
        image: String? = null,
        subject: String? = "無題",
        author: String? = "名無し",
        saidane: String? = null
    ) = Post(
        id = id, author = author, subject = subject, timestamp = timestamp, messageHtml = body,
        imageUrl = image, thumbnailUrl = image?.replace("/src/", "/thumb/")?.replace(".jpg", "s.jpg"), saidaneLabel = saidane
    )

    private fun noop() = Unit

    private fun assertExtrasLast(actions: List<FutaberPostAction>, ownCount: Int) {
        assertTrue(actions.take(ownCount).none { it.extra }, "the original's items are not extras: ${actions.map { it.id }}")
        assertTrue(actions.drop(ownCount).all { it.extra }, "everything after the original's last item is an extra: ${actions.map { it.id }}")
    }

    // ---- M21 ----

    @Test fun thePostSheetListsTheOriginalsSevenItemsThenTheExtras() {
        val first = post("100")
        val target = post("101", body = "一行目<br>二行目")
        val remote = mutableListOf<FutaberRemoteAction>()
        val quoted = mutableListOf<String>()
        var copied = 0
        var ng = 0
        var marked = 0
        val actions = futaberPostSheetActions(
            target, onQuote = { quoted += it }, onCopy = { copied++ }, onRemote = { remote += it },
            extras = futaberPostSheetExtras(
                onNgRegister = { ng++ }, markEnabled = true, onMark = { marked++ },
                detail = listOf(FutaberPostAction("select-copy", "項目を選んでコピー") {})
            )
        )
        assertEquals(
            listOf("quote", "quote-number", "quote-last", "copy", "delete-own", "report", "saidane", "ng-register", "mark", "select-copy"),
            actions.map { it.id }
        )
        assertEquals(listOf("引用", "No.引用", "最終行引用", "コピー", "レス削除", "不適切な投稿を通報", "そうだね"), actions.take(7).map { it.label })
        assertExtrasLast(actions, 7)
        actions.forEach { it.onClick() }
        assertEquals(listOf(FutaberRemoteAction.DeleteOwn, FutaberRemoteAction.Report, FutaberRemoteAction.Saidane), remote)
        assertEquals(listOf(">一行目\n>二行目", ">No.101", ">二行目"), quoted)
        assertEquals(1, copied); assertEquals(1, ng); assertEquals(1, marked)
        assertEquals("レス:1", futaberPostSheetTitle(listOf(first, target), target))
        // On an SE the group stops at そうだね: seven 57pt rows, with NG登録 and マーク scrolling inside it.
        val rows = actions.map { 57 }
        assertEquals(7 * 57, futaberSheetOwnRowsHeight(rows, actions.map { it.extra }))
    }

    /** The extras of every sheet come after the original's last item (where F-B2 puts 「その他の機能」). */
    @Test fun extrasComeOnlyAfterTheOriginalsLastItemInEverySheet() {
        val target = post("1", image = "https://may.2chan.net/b/src/1.jpg")
        val extra = listOf(FutaberPostAction("x", "x") {})
        val sheets = mapOf(
            "post" to (futaberPostSheetActions(target, {}, {}, {}, futaberPostSheetExtras({}, false, {}, extra)) to 7),
            "picture" to (futaberImageSheetActions(
                "u", true, true, lens = true, {}, {}, {}, {}, {}, {}, {}, futaberImageSheetExtras("u", true, {}, extra)
            ) to 7),
            "catalog" to (futaberCatalogSheetActions(true, true, {}, {}, {}, {}, futaberCatalogSheetExtras({}, {}, extra.first())) to 4),
            "tab" to (futaberTabSheetActions(true, true, true, {}, {}, {}, {}, {}, futaberTabSheetExtras(extra.first(), extra.first())) to 5),
            "report" to (futaberReportConfirmActions({}, futaberReportConfirmExtras(true) {}) to 1),
            "ip" to (futaberIpSheetActions {} to 1)
        )
        sheets.forEach { (name, sheet) ->
            val (actions, own) = sheet
            val lastOwn = actions.indexOfLast { !it.extra }
            val firstExtra = actions.indexOfFirst { it.extra }
            assertEquals(own - 1, lastOwn, "$name: ${actions.map { it.id }}")
            assertTrue(firstExtra == -1 || firstExtra == own, "$name: ${actions.map { it.id }}")
        }
        // An item made by an extension stays an extra wherever it is passed in.
        assertTrue(futaberAsExtras(listOf(FutaberPostAction("y", "y") {}, FutaberPostAction("z", "z", extra = true) {})).all { it.extra })
    }

    // ---- M05 ----

    @Test fun theCatalogSheetOffersHistoryRemovalOnlyWhenTheThreadIsInTheHistory() {
        val ran = mutableListOf<String>()
        fun sheet(inHistory: Boolean) = futaberCatalogSheetActions(
            inHistory, ngThreadEnabled = false,
            onUrlScheme = { ran += "url-scheme" }, onCopyUrl = { ran += "copy-url" }, onNgThread = { ran += "ng-thread" },
            onRemoveHistory = { ran += "remove-history" },
            extras = futaberCatalogSheetExtras({ ran += "browser" }, { ran += "report" }, null)
        )
        val notRead = sheet(false)
        assertEquals(listOf("url-scheme", "copy-url", "ng-thread", "browser", "report"), notRead.map { it.id })
        assertEquals(listOf("URLスキームアクション", "URLをコピー", "スレッドをNG"), notRead.take(3).map { it.label })
        assertExtrasLast(notRead, 3)
        assertFalse(notRead[2].enabled)
        val read = sheet(true)
        assertEquals(listOf("url-scheme", "copy-url", "ng-thread", "remove-history", "browser", "report"), read.map { it.id })
        assertTrue(read[3].destructive)
        assertEquals("履歴から削除", read[3].label)
        assertExtrasLast(read, 4)
        read.forEach { it.onClick() }
        assertEquals(listOf("url-scheme", "copy-url", "ng-thread", "remove-history", "browser", "report"), ran)
    }

    // ---- M25・C26 ----

    @Test fun thePictureSheetIsTitledByTheFileNameAndListsTheOriginalsSixItems() {
        val url = "https://may.2chan.net/27/src/1792153182260.jpg"
        val target = post("5", image = url)
        assertEquals("1792153182260.jpg", futaberImageSheetTitle(target, listOf(target)))
        // Only a thumbnail: its file name; nothing: 「レス:Nの画像」.
        val thumbOnly = post("6").copy(thumbnailUrl = "https://may.2chan.net/27/thumb/42s.jpg")
        assertEquals("42s.jpg", futaberImageSheetTitle(thumbOnly, listOf(target, thumbOnly)))
        assertEquals("レス:1の画像", futaberImageSheetTitle(post("7"), listOf(target, post("7"))))

        val ran = mutableListOf<String>()
        fun sheet(lens: Boolean, address: String? = url, services: Boolean = true) = futaberImageSheetActions(
            address, viewerAvailable = true, servicesAvailable = services, lens = lens,
            onView = { ran += "view" }, onBrowser = { ran += "browser:$it" }, onAscii2d = { ran += "ascii2d:$it" },
            onUrlScheme = { ran += "url-scheme" }, onCopyUrl = { ran += "copy:$it" }, onNg = { ran += "ng:$it" },
            onLens = { ran += "lens:$it" },
            extras = futaberImageSheetExtras(address, services, onSearch = { ran += "search:$it" },
                menu = futaberImageMenuExtraActions(true, address, null))
        )
        val plain = sheet(lens = false)
        assertEquals(
            listOf("image-viewer", "image-external", "image-ascii2d", "image-url-scheme", "image-copy-url", "image-ng",
                "image-search", "image-save", "image-share"),
            plain.map { it.id }
        )
        assertEquals(
            listOf("画像を表示", "ブラウザで開く", "二次元画像詳細検索", "URLスキームアクション", "画像のURLをコピー", "画像をNG"),
            plain.take(6).map { it.label }
        )
        assertExtrasLast(plain, 6)
        plain.take(7).forEach { it.onClick() }
        assertEquals(listOf("view", "browser:$url", "ascii2d:$url", "url-scheme", "copy:$url", "ng:$url", "search:$url"), ran)
        // Lens (the setting on) comes after the sixth, before the extras.
        val withLens = sheet(lens = true)
        assertEquals("image-lens", withLens[6].id)
        assertEquals("GoogleLens検索", withLens[6].label)
        assertExtrasLast(withLens, 7)
        // No address: only 「URLスキームアクション」 (which only closes) can be pressed; no services: no search and no NG.
        assertEquals(listOf("image-url-scheme"), sheet(lens = true, address = null).filter { it.enabled }.map { it.id })
        assertEquals(
            listOf("image-viewer", "image-external", "image-url-scheme", "image-copy-url"),
            sheet(lens = true, services = false).take(7).filter { it.enabled }.map { it.id }
        )
    }

    // ---- The tab sheet ----

    @Test fun theTabSheetKeepsItsFiveItemsThenRestoreAndCheckAll() {
        val restore = FutaberPostAction("tab-restore", "閉じたタブを元に戻す（1）") {}
        val check = FutaberPostAction("tab-check-all", "全タブを確認") {}
        val actions = futaberTabSheetActions(
            hasRight = false, hasOthers = true, hasFallen = false, {}, {}, {}, {}, {}, futaberTabSheetExtras(restore, check)
        )
        assertEquals(
            listOf("tab-remove", "tab-remove-right", "tab-remove-others", "tab-remove-fallen", "tab-remove-all", "tab-restore", "tab-check-all"),
            actions.map { it.id }
        )
        assertEquals(listOf(true, false, true, false, true), actions.take(5).map { it.enabled })
        assertTrue(actions[4].destructive)
        assertExtrasLast(actions, 5)
        assertEquals(5, futaberTabSheetActions(true, true, true, {}, {}, {}, {}, {}, futaberTabSheetExtras(null, null)).size)
    }

    // ---- M24 ----

    @Test fun theReportIsConfirmedWithASheetThatSendsDelOnly() {
        val sent = mutableListOf<Boolean>()
        val plain = futaberReportConfirmActions({ sent += false }, futaberReportConfirmExtras(false) { sent += true })
        assertEquals(listOf("del-send"), plain.map { it.id })
        assertEquals("delを送信", plain.single().label)
        plain.single().onClick()
        assertEquals(listOf(false), sent)
        // The extension 「delの確認でこのレスをNGにも登録」: a second way to send, after the original's item.
        val withNg = futaberReportConfirmActions({ sent += false }, futaberReportConfirmExtras(true) { sent += true })
        assertEquals(listOf("del-send", "del-send-ng"), withNg.map { it.id })
        assertExtrasLast(withNg, 1)
        withNg[1].onClick()
        assertEquals(listOf(false, true), sent)
    }

    // ---- G28 ----

    @Test fun theIpSheetAddsTheIpAsWrittenToTheSharedNgHeaders() {
        val ip = "IP:106.73.*(enabler.ne.jp)"
        assertEquals(ip, futaberIpNgEntry(ip))
        assertEquals("IP:106.73.*(enabler.ne.jp)", futaberIpNgEntry(" 106.73.*(enabler.ne.jp) "))
        val actions = futaberIpSheetActions {}
        assertEquals(listOf("ip-ng"), actions.map { it.id })
        assertEquals("IPをNG", actions.single().label)
        val added = futaberAddNgEntry(listOf("ID:abcd"), futaberIpNgEntry(ip))
        assertEquals(listOf("ID:abcd", ip), added)
        // Added once (case-insensitively, as the lists are), and the notice says where it went.
        assertEquals(added, futaberAddNgEntry(added, "ip:106.73.*(ENABLER.ne.jp)"))
        assertEquals("NGネーム・ID等に「$ip」を追加しました", futaberIpNgNotice(listOf("ID:abcd"), ip))
        assertTrue(futaberIpNgNotice(List(FUTABER_NG_MAX_ENTRIES) { "w$it" }, ip).startsWith("登録できるのは"))
    }

    /**
     * The entry hides the posts of that IP and no other, in ふたばー風 (the header lists), ふたちゃ (CanonicalNgRules over
     * the header text), ふたったー風 (its common NG uses the same lists) and としあき(仮) (its common NG reads 「IP:…」 as a
     * header word and as that poster).
     */
    @Test fun anIpNgEntryHidesThatPostersPostsInEveryModeAndNoOther() {
        val a = "IP:106.73.*(enabler.ne.jp)"
        val posts = listOf(
            post("1", timestamp = "26/10/16(金)21:19:42 $a"),
            post("2", timestamp = "26/10/16(金)22:37:55 IP:130.62.*(transix.jp)"),
            post("3", timestamp = "26/10/17(土)15:26:11 $a"),
            post("4", timestamp = "26/10/17(土)16:00:00 ID:xyz12345")
        )
        val headers = listOf(futaberIpNgEntry(a))
        assertEquals(setOf("1", "3"), futaberNgHiddenIds(posts, emptyList(), headers, emptyList(), "", ""))
        assertEquals(setOf(0, 2), ftutterCommonNgPosts(posts, emptyList(), headers))
        val futacha = CanonicalNgRules.of(headers, emptyList())
        assertEquals(listOf(true, false, true, false), posts.map { futacha.matchesHeader(it) })
        val index = buildCompatThreadNgRuleIndex(compatCommonNgFilterRules(headers, emptyList(), emptyList()), "tab", "board")
        val snapshots = ThreadPage("1", null, null, null, posts).toCompatThreadSnapshot("tab", 0).posts
        assertEquals(listOf(true, false, true, false), snapshots.map { it.matchesCompatThreadNg(index) })
        // The ID entries other modes add keep working beside it.
        assertEquals(setOf("1", "3", "4"), futaberNgHiddenIds(posts, emptyList(), headers + "ID:xyz12345", emptyList(), "", ""))
    }

    // ---- M23 ----

    @Test fun theCopyPanelShowsTheHeaderAndSelectsTheBody() {
        val target = post(
            "325109", body = "なんまんだぶ<br>お疲れさまでした", timestamp = "26/10/16(金)22:37:55 IP:130.62.*(transix.jp)",
            image = "https://may.2chan.net/27/src/1792153182260.jpg", saidane = "そうだねx28"
        )
        val model = futaberCopyPanelText(target)
        assertEquals("無題 名無し 26/10/16(金)22:37:55 IP:130.62.*(transix.jp) No.325109 そうだねx28", model.header)
        assertEquals("なんまんだぶ\nお疲れさまでした\n1792153182260.jpg", model.body)
        assertEquals(model.body, model.text.substring(model.bodyStart))
        assertEquals(model.header, model.text.substring(0, model.bodyStart - 1))
        // No そうだね, no subject or name: only what the post has.
        val bare = futaberCopyPanelText(post("9", body = "本文", subject = null, author = null))
        assertEquals("26/10/16(金)22:37:55 No.9", bare.header)
        assertEquals("本文", bare.body)
        // 引用 of a selection: each line that is not blank after ">".
        assertEquals(">お疲れさまでした\n>x", futaberQuoteSelection("お疲れさまでした\n\n  x "))
        assertEquals(listOf("コピー", "共有…", "引用", "NGに追加"), FutaberCopyMenuItem.entries.map { it.label })
    }

    @Test fun theOldInstantCopyAndLensAreOffUntilTurnedOn() {
        val none = FutaberDisplaySettings.from(emptyMap())
        assertFalse(none.instantCopy)
        assertFalse(none.googleLens)
        val on = FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.INSTANT_COPY to "ON", FutaberSettingKeys.GOOGLE_LENS to "ON"))
        assertTrue(on.instantCopy)
        assertTrue(on.googleLens)
        val off = FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.INSTANT_COPY to "OFF", FutaberSettingKeys.GOOGLE_LENS to "OFF"))
        assertFalse(off.instantCopy)
        assertFalse(off.googleLens)
    }

    /** 二次元画像詳細検索 sends the picture's address to ascii2d's form; GoogleLens検索 only builds Lens's address. */
    @Test fun theDirectSearchesAskTheRightServiceOnlyWhenRun() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine { request ->
            requests += request
            respond("""<a href="https://ascii2d.net/search/color/abc123">r</a>""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "text/html"))
        })
        try {
            val url = "https://may.2chan.net/b/src/1792153182260.jpg"
            val ascii2d = futaberAscii2dSearch(url)
            assertEquals(CompatImageSearchTarget.ASCII2D_URL, ascii2d.target)
            assertTrue(requests.isEmpty(), "nothing is sent before the search runs")
            val found = assertIs<CompatImageSearchResult.RemoteUrl>(futaberRunDirectImageSearch(client, ascii2d, emptyMap()).getOrThrow())
            assertEquals("https://ascii2d.net/search/color/abc123", found.url)
            assertEquals(1, requests.size)
            assertEquals(DEFAULT_COMPAT_ASCII2D_ENDPOINT, requests.single().url.toString())
            assertEquals(url, assertIs<FormDataContent>(requests.single().body).formData["uri"])
            // Lens: its address with the picture's, and no request.
            val lens = assertIs<CompatImageSearchResult.RemoteUrl>(futaberRunDirectImageSearch(client, futaberLensSearch(url), emptyMap()).getOrThrow())
            assertEquals("https://lens.google.com/uploadbyurl?url=https%3A%2F%2Fmay.2chan.net%2Fb%2Fsrc%2F1792153182260.jpg", lens.url)
            assertEquals(1, requests.size)
            // ascii2d cannot take a GIF: told why, nothing sent.
            val gif = futaberRunDirectImageSearch(client, futaberAscii2dSearch("https://may.2chan.net/b/src/1.gif"), emptyMap())
            assertEquals("GIF・WebM・MP4は検索できません", gif.exceptionOrNull()?.message)
            assertEquals(1, requests.size)
        } finally {
            client.close()
        }
    }
}
