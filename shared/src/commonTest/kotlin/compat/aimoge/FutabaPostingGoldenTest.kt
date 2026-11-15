package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.diffCompatCatalogGenerations
import com.valoser.futacha.shared.compat.toCompatPostSnapshot
import com.valoser.futacha.shared.media.source.isSharedOriginalImageUrl
import com.valoser.futacha.shared.media.source.isSharedOriginalVideoUrl
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.numericId
import com.valoser.futacha.shared.network.defaultBoardPostingCapabilities
import com.valoser.futacha.shared.network.unconfirmedHttpBoardApiActionResult
import com.valoser.futacha.shared.network.unconfirmedHttpBoardApiPostResult
import com.valoser.futacha.shared.ui.board.buildPostingAwareFailureMessage
import com.valoser.futacha.shared.ui.board.shouldOfferCookieManagerForPostingFailure
import com.valoser.futacha.shared.ui.compat.applyCompatMailPreset
import com.valoser.futacha.shared.ui.compat.compatPostMailPresets
import com.valoser.futacha.shared.ui.compat.compatPostShiftJisByteCount
import com.valoser.futacha.shared.ui.compat.toCompatUserMessage
import com.valoser.futacha.shared.ui.ftutter.FTUTTER_SOUDANE_FAILED
import com.valoser.futacha.shared.ui.ftutter.FtutterDeleteKeySync
import com.valoser.futacha.shared.ui.ftutter.ftutterDeleteKeySync
import com.valoser.futacha.shared.ui.ftutter.ftutterNormalizeDeleteKey
import com.valoser.futacha.shared.ui.ftutter.ftutterRemoteSuccessNotice
import com.valoser.futacha.shared.ui.ftutter.ftutterSaidaneCounted
import com.valoser.futacha.shared.ui.futaber.futaberCameraMenu
import kotlin.test.Test
import kotlin.test.assertEquals

/** AM-B0: today's posting capabilities, delete key, failure texts and post tools must stay as is for Futaba boards after あいもげ support. */
class FutabaPostingGoldenTest {
    @Test
    fun defaultPostingCapabilities() {
        val all = listOf("gif", "jpeg", "jpg", "mp4", "png", "webm", "webp")
        val expected = mapOf(
            "https://may.2chan.net/b/futaba.php" to "8192000|$all|true|true|true",
            "https://img.2chan.net/b/futaba.php" to "3072000|[gif, jpeg, jpg, png]|false|false|false",
            "https://dat.2chan.net/b/futaba.php" to "3072000|$all|true|true|true",
            "https://may.2chan.net/27/futaba.php" to "3072000|$all|true|true|true",
        )
        expected.forEach { (board, golden) ->
            val c = defaultBoardPostingCapabilities(board)
            assertEquals(golden, "${c.maxFileSizeBytes}|${c.supportedExtensions.sorted()}|${c.replyAttachmentsAllowed}|${c.nameAllowed}|${c.subjectAllowed}", board)
        }
    }

    /** J7: ふたったー風's own cut. The other two `take(8)` (AppStatePreferenceOperationsSupport.setLastUsedDeleteKey,
     *  AppStatePersistenceSupport defaults) sit behind the store and are not called here. */
    @Test
    fun deleteKeyIsCutToEightAlnum() {
        assertEquals("abcd1234", ftutterNormalizeDeleteKey("ab-cd1234XYZ"))
    }

    @Test
    fun ftutterDeleteKeySyncResults() {
        assertEquals(FtutterDeleteKeySync("GEN", "GEN"), ftutterDeleteKeySync(null, null, null) { "GEN" })
        assertEquals(FtutterDeleteKeySync(own = "s"), ftutterDeleteKeySync(null, "s", null) { "G" })
        assertEquals(FtutterDeleteKeySync(shared = "o"), ftutterDeleteKeySync("o", null, null) { "G" })
        assertEquals(FtutterDeleteKeySync(own = "s"), ftutterDeleteKeySync("o", "s", null) { "G" })
        assertEquals(FtutterDeleteKeySync(own = "s"), ftutterDeleteKeySync("o", "s", "o") { "G" })
        assertEquals(FtutterDeleteKeySync(shared = "o"), ftutterDeleteKeySync("o", "s", "s") { "G" })
    }

    @Test
    fun unconfirmedTextsAndHowScreensTellThemApart() {
        val post = unconfirmedHttpBoardApiPostResult("返信に失敗しました", Exception("boom"))
        val act = unconfirmedHttpBoardApiActionResult("削除", "（再読み込みで確認）", Exception("boom"))
        assertEquals("送信結果を確認できませんでした。スレッドを確認してから再送してください。返信に失敗しました: boom", post.message)
        assertEquals("削除の結果を確認できませんでした（再読み込みで確認）: boom", act.message)
        // Screens tell an action apart by HTTP_BOARD_API_ACTION_RESULT_UNCONFIRMED in the message.
        assertEquals("FB", post.toCompatUserMessage("FB"))
        assertEquals("送信しましたが、結果を確認できませんでした。スレッドを再読み込みして確認してください", act.toCompatUserMessage("FB"))
        assertEquals("返信の送信に失敗しました: ${post.message}", buildPostingAwareFailureMessage("返信の送信に失敗しました", post))
        assertEquals(act.message, buildPostingAwareFailureMessage("返信の送信に失敗しました", act))
    }

    @Test
    fun failureTextsAndCookieOffer() {
        val cases = listOf(
            "HTTP error 404" to "ページは見つかりません（404）|false",
            "HTTP 410" to "ページは消えています（410）|false",
            "Socket timeout" to "通信がタイムアウトしました|false",
            "Unable to resolve host x" to "ネットワークに接続できません|false",
            "書き込み規制中です" to "FB|false",
            "Cookieを有効にしてください" to "FB|true",
        )
        cases.forEach { (msg, golden) ->
            val e = Exception(msg)
            assertEquals(golden, "${e.toCompatUserMessage("FB")}|${shouldOfferCookieManagerForPostingFailure(e)}", msg)
            assertEquals("返信の送信に失敗しました: $msg", buildPostingAwareFailureMessage("返信の送信に失敗しました", e))
        }
        assertEquals(false, shouldOfferCookieManagerForPostingFailure(unconfirmedHttpBoardApiPostResult("x", Exception("y"))))
    }

    /** としあき(仮)'s presets. ふたちゃ (ThreadScreenOverlayHost / CatalogSearchDialogComponents) and ふたばー風's write
     *  settings hold `listOf("ID表示", "IP表示", "sage")` inline in Composables, and ふたったー風 has no preset buttons;
     *  those are not reachable as functions. */
    @Test
    fun mailPresets() {
        assertEquals(listOf("ID表示", "IP表示", "sage"), compatPostMailPresets(true))
        assertEquals(listOf("sage"), compatPostMailPresets(false))
        assertEquals(listOf("id表示", "ip表示", "sage", "old"),
            listOf("ID表示", "IP表示", "sage", "x").map { applyCompatMailPreset("old", it, true) })
    }

    @Test
    fun shiftJisByteCount() {
        assertEquals(listOf(0, 3, 6, 4, 9), listOf("", "abc", "あいう", "a\nb", "😀").map(::compatPostShiftJisByteCount))
    }

    /** ふたばー風: あぷ小 shows only with the 書き込みツール extension, enabled only when uploading is possible. */
    @Test
    fun upsToolCondition() {
        val base = "recent:true, file:true, library:true"
        val tools = "tool-voice:true, tool-network-info:true, tool-device-info:true, tool-multi:true"
        fun menu(a: Boolean, t: Boolean, u: Boolean) = futaberCameraMenu(a, t, u).joinToString { "${it.id}:${it.enabled}" }
        assertEquals(base, menu(false, false, false))
        assertEquals(base, menu(false, false, true))
        assertEquals("$base, tool-drawing:true, tool-ups:false, $tools", menu(false, true, false))
        assertEquals("$base, tool-drawing:true, tool-ups:true, $tools", menu(false, true, true))
        assertEquals("$base, tool-drawing:true", menu(true, false, true))
        assertEquals("$base, tool-drawing:true, tool-ups:true, $tools", menu(true, true, true))
    }

    /** M87: the texts ふたったー風 builds from voteSaidaneResponse / requestDeletionResponse / deleteByUserResponse. */
    @Test
    fun ftutterResponseTexts() {
        assertEquals("<html><body>ok</body></html>", ftutterRemoteSuccessNotice(" <html><body>ok</body></html> ", false, "L"))
        assertEquals("L", ftutterRemoteSuccessNotice("x", true, "L"))
        assertEquals(false, ftutterSaidaneCounted("0"))
        assertEquals(true, ftutterSaidaneCounted(" 3 "))
        assertEquals(true, ftutterSaidaneCounted(""))
        assertEquals("失敗しました", FTUTTER_SOUDANE_FAILED)
    }

    @Test
    fun postSnapshot() {
        val post = Post(id = "1439323741", author = "としあき", subject = "無題", timestamp = "26/11/10(月)12:00:00",
            posterId = "ID:abcd", messageHtml = "&gt;&gt;1", imageUrl = "https://may.2chan.net/b/src/1.jpg",
            thumbnailUrl = "https://may.2chan.net/b/thumb/1s.jpg", mail = "sage", saidaneLabel = "そうだねx2")
        assertEquals(
            "CompatPostSnapshot(position=3, postNo=1439323741, author=としあき, subject=無題, mail=sage, timestamp=26/11/10(月)12:00:00, posterId=ID:abcd, messageHtml=&gt;&gt;1, imageUrl=https://may.2chan.net/b/src/1.jpg, thumbnailUrl=https://may.2chan.net/b/thumb/1s.jpg, saidaneLabel=そうだねx2, isDeleted=false, isIsolated=false, isContentRedacted=false, referencedCount=0, thumbnailWidth=null, thumbnailHeight=null, imageFileSizeBytes=null, mediaKey=null, quoteReferences=[], quoteReferencesParsed=false)",
            withoutSiteDefaults(post.toCompatPostSnapshot(3).toString()))
    }

    @Test
    fun numericIds() {
        assertEquals(listOf(1439323740L, 123L, 0L, 0L),
            listOf("1439323740", "abc123", "abc", "").map { CatalogItem(it, "u", null, null, null, replyCount = 0).numericId() })
    }

    @Test
    fun catalogGenerationDiff() {
        fun ci(id: Long) = CatalogItem(id.toString(), "https://may.2chan.net/b/res/$id.htm", null, null, null, replyCount = 0)
        val cur = (100L..109L).map(::ci)
        val d1 = diffCompatCatalogGenerations(cur, listOf(ci(105), ci(104), ci(99), ci(108), ci(103)), 10, true)
        assertEquals(emptyList(), d1.vanishedWithin + d1.vanishedBottom)
        val cur2 = cur.filter { it.id != "103" && it.id != "108" }
        val d2 = diffCompatCatalogGenerations(cur2, cur + ci(99), 20, true)
        assertEquals(listOf("108", "103"), d2.vanishedWithin.map { it.id })
        assertEquals(listOf("99"), d2.vanishedBottom.map { it.id })
        val off = diffCompatCatalogGenerations(cur2, cur, 20, false)
        assertEquals(emptyList(), off.vanishedWithin + off.vanishedBottom)
    }

    @Test
    fun sharedOriginalUrls() {
        val expected = mapOf(
            "https://may.2chan.net/b/src/1.jpg" to "true|false",
            "http://img.2chan.net/b/src/1.PNG" to "true|false",
            "https://may.2chan.net/b/thumb/1s.jpg" to "false|false",
            "https://may.2chan.net/b/src/1.webm" to "false|true",
            "https://may.2chan.net/b/src/1.mp4" to "false|true",
            "https://example.com/b/src/1.jpg" to "false|false",
            "ftp://may.2chan.net/b/src/1.jpg" to "false|false",
            "https://dec.2chan.net/up2/src/f1.gif" to "true|false",
            "https://may.2chan.net/b/src/1.jpg#compat-reload=3" to "true|false",
        )
        expected.forEach { (u, g) -> assertEquals(g, "${isSharedOriginalImageUrl(u)}|${isSharedOriginalVideoUrl(u)}", u) }
    }
}
