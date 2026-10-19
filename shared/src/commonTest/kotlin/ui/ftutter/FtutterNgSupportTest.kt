package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.Post
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FtutterNgSupportTest {
    @Test fun hiddenCatalogReasonsComeFromTheSameScopedMatcherAndCommonWords() {
        val items = listOf(item("1", "ネコの話"), item("2", "共通ワード"), item("3", "残す"))
        val rules = listOf(FtutterNgRule("ねこ", 0), FtutterNgRule("残す", 0, ita = FtutterNgBoard("img", "https://img.2chan.net/b/")))
        val table = ftutterCatalogTitleNg(items, FtutterNgMatcher(rules, may), listOf("共通"))
        assertEquals("NGタイトル: ねこ", table.byTitle.getValue("ネコの話").reason)
        assertEquals("共通のカタログNG: 共通", table.byTitle.getValue("共通ワード").reason)
        assertFalse(table.byTitle.getValue("残す").hidden)
        assertNull(table.byTitle.getValue("残す").reason)
    }

    @Test fun catalogSearchSessionIsKeptPerBoardAcrossScreenRecreation() {
        val holder = FtutterCatalogHolder()
        holder.search("a").apply { open = true; query = "候補" }
        holder.search("b").apply { query = "別の板" }
        assertEquals("候補", holder.search("a").query)
        assertTrue(holder.search("a").open)
    }
    private val may = "https://may.2chan.net/b/futaba.php"
    private fun post(id: String, body: String = "本文", posterId: String? = null) = Post(
        id = id, author = null, subject = null, timestamp = "", posterId = posterId, messageHtml = body, imageUrl = null, thumbnailUrl = null
    )
    private fun item(id: String, title: String) = CatalogItem(
        id = id, threadUrl = "https://may.2chan.net/b/res/$id.htm", title = title, thumbnailUrl = null, fullImageUrl = null, replyCount = 0
    )

    @Test
    fun theOriginalBackupShapeIsReadAndUnknownTypesAreDropped() {
        // 原作の StringList（1行1件のJSON）。isReg・isActive が無ければ false・true。
        val raw = """{"text":"あ","type":1}
{"text":"ID:abc","type":2,"isActive":false,"ita":{"name":"虹裏may","url":"https://may.2chan.net/b/"}}
{"text":"x","type":9}"""
        val rules = parseFtutterNgList(raw)
        assertEquals(2, rules.size)
        assertEquals(FtutterNgRule("あ", 1, isReg = false, isActive = true), rules[0])
        assertEquals("https://may.2chan.net/b/", rules[1].ita?.url)
        assertEquals(rules, parseFtutterNgList(encodeFtutterNgList(rules)))
    }

    @Test
    fun registrationIsCheckedAsTheOriginalPlusTheRegex() {
        val existing = listOf(FtutterNgRule("a", 1))
        assertEquals("文字列が空です！", ftutterNgValidationError(FtutterNgRule("", 1), existing))
        assertEquals("既に同じものが存在します！", ftutterNgValidationError(FtutterNgRule("a", 1), existing))
        assertNull(ftutterNgValidationError(FtutterNgRule("a", 0), existing))
        assertEquals("'ID:'または'IP:'を含めて入力してください！", ftutterNgValidationError(FtutterNgRule("abc", 2), existing))
        assertNull(ftutterNgValidationError(FtutterNgRule("IP:1.2", 2), existing))
        // 原作は検証せず、不正な`[`で本文やカタログが出なくなった。
        assertEquals("正規表現が正しくありません", ftutterNgValidationError(FtutterNgRule("[", 1, isReg = true), existing))
    }

    @Test
    fun wordsAreMatchedLooselyRegexesStrictlyAndIdsExactly() {
        val m = FtutterNgMatcher(
            listOf(
                FtutterNgRule("ネコ", 1), FtutterNgRule("^犬$", 1, isReg = true), FtutterNgRule("ID:abc", 2),
                FtutterNgRule("[", 1, isReg = true) // 保存済みの不正なルールは飛ばすだけ
            ),
            may
        )
        assertTrue(m.hidesPost(post("1", "かわいいねこ")))
        assertTrue(m.hidesPost(post("2", "犬")))
        assertFalse(m.hidesPost(post("3", "犬だ")))
        assertTrue(m.hidesPost(post("4", posterId = "ID:abc")))
        assertFalse(m.hidesPost(post("5", posterId = "ID:abcd")))
    }

    @Test
    fun boardScopedAndInactiveRulesApplyOnlyWhereTheyShould() {
        val rules = listOf(
            FtutterNgRule("板だけ", 1, ita = FtutterNgBoard("may", "http://may.2chan.net/b/")),
            FtutterNgRule("無効", 1, isActive = false)
        )
        assertTrue(FtutterNgMatcher(rules, may).hidesPost(post("1", "板だけ")))
        assertFalse(FtutterNgMatcher(rules, "https://img.2chan.net/b/futaba.php").hidesPost(post("1", "板だけ")))
        assertFalse(FtutterNgMatcher(rules, may).hidesPost(post("1", "無効")))
    }

    @Test
    fun theCatalogDropsNgTitlesThenPullsPickupsForwardInOrder() {
        val items = listOf(item("1", "雑談"), item("2", "ねこスレ"), item("3", "荒らし"), item("4", "ネコ部"))
        val m = FtutterNgMatcher(listOf(FtutterNgRule("荒らし", 0), FtutterNgRule("ねこ", 3)), may)
        val result = ftutterApplyCatalogNg(items, m, FtutterOrder.CATALOG, FtutterOrder.entries.toSet())
        assertEquals(listOf("2", "4", "1"), result.items.map { it.id })
        assertEquals(listOf("3"), result.ngRemoved.map { it.id })
        assertEquals("ねこ", result.pickupWords[items[1].threadUrl])
        // 今の並び順が対象でなければ、寄せも印もしない（NGは効く）。
        val off = ftutterApplyCatalogNg(items, m, FtutterOrder.NEW, setOf(FtutterOrder.CATALOG))
        assertEquals(listOf("1", "2", "4"), off.items.map { it.id })
        assertTrue(off.pickupWords.isEmpty())
    }

    @Test
    fun pickupOrdersDefaultToAllAndRoundTrip() {
        assertEquals(FtutterOrder.entries.toSet(), parseFtutterPickupOrders(null))
        val some = setOf(FtutterOrder.CATALOG, FtutterOrder.SOUDANE)
        assertEquals(some, parseFtutterPickupOrders(encodeFtutterPickupOrders(some)))
        assertEquals(setOf(FtutterOrder.NEW), parseFtutterPickupOrders("OrderMode.newnew\nOrderMode.previous"))
    }

    @Test
    fun draftsAreKeptPerThreadOrBoardAndClearedWhenEmpty() {
        val reply = ftutterDraftKey(FtutterPostTarget.Reply("b", "123"), "https://may.2chan.net/b/futaba.php")
        val create = ftutterDraftKey(FtutterPostTarget.Create("b"), "http://may.2chan.net/b/")
        assertEquals("res:may.2chan.net/b/res/123.htm", reply)
        assertEquals("board:may.2chan.net/b/", create)
        val saved = parseFtutterDrafts(ftutterDraftsWith(emptyMap(), reply, FtutterDraft(comment = "途中")))
        assertEquals("途中", saved[reply]?.comment)
        assertTrue(parseFtutterDrafts(ftutterDraftsWith(saved, reply, FtutterDraft())).isEmpty())
    }

    @Test
    fun theQuoteIsAddedAfterTheDraftAsTheOriginalDoes() {
        assertEquals(">a\n>b\n", ftutterQuoteLines(listOf("a", "b")))
        assertEquals(">No.1\n", ftutterInitialComment("", ">No.1\n"))
        assertEquals("書きかけ\n>No.1\n", ftutterInitialComment("書きかけ", ">No.1\n"))
        assertEquals("書きかけ\n>No.1\n", ftutterInitialComment("書きかけ\n", ">No.1\n"))
    }

    @Test
    fun theDeleteKeyIsEightAlphanumericsWithoutW() {
        val key = ftutterGenerateDeleteKey(Random(1))
        assertEquals(8, key.length)
        assertTrue(key.all { it.isLetterOrDigit() && it != 'W' })
        assertEquals("abc12345", ftutterNormalizeDeleteKey("abc-1234567"))
    }

    /** 監査M6：添付の誤りがあっても、送信の検査・失敗の理由が隠れない。 */
    @Test
    fun anAttachmentErrorDoesNotHideTheOtherReason() {
        assertEquals(listOf("サイズが大きすぎます", "削除キーを入力してください"), ftutterPostErrors("サイズが大きすぎます", "削除キーを入力してください"))
        assertEquals(listOf("削除キーを入力してください"), ftutterPostErrors(null, "削除キーを入力してください"))
        assertEquals(emptyList(), ftutterPostErrors(null, " "))
        assertEquals(listOf("同じ"), ftutterPostErrors("同じ", "同じ"))
    }

    /** 監査M7：「あぷ小」のアップロード中は、書き込むも閉じるも押せない。 */
    @Test
    fun anUploadInProgressBlocksSendingAndClosing() {
        assertTrue(ftutterPostCanSend(sending = false, attachmentProcessing = false, uploading = false))
        assertFalse(ftutterPostCanSend(sending = false, attachmentProcessing = false, uploading = true))
        assertFalse(ftutterPostCanSend(sending = true, attachmentProcessing = false, uploading = false))
        assertFalse(ftutterPostCanSend(sending = false, attachmentProcessing = true, uploading = false))
        assertTrue(ftutterPostCanClose(sending = false, uploading = false))
        assertFalse(ftutterPostCanClose(sending = false, uploading = true))
        assertFalse(ftutterPostCanClose(sending = true, uploading = false))
    }

    /** 監査L12：ピックアップの並び順をすべて外すと「なし」のまま残る（以前は「全て」に戻った）。 */
    @Test
    fun anEmptyPickupSelectionStaysEmpty() {
        assertEquals(emptySet(), parseFtutterPickupOrders(encodeFtutterPickupOrders(emptySet())))
        assertEquals(FtutterOrder.entries.toSet(), parseFtutterPickupOrders(null))
        val one = setOf(FtutterOrder.entries.first())
        assertEquals(one, parseFtutterPickupOrders(encodeFtutterPickupOrders(one)))
    }
}
