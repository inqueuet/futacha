package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.BoardSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FtutterBackupImportTest {
    private val list = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu!"
    private fun esc(s: String) = s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
    private fun listXml(name: String, items: List<String>) =
        "<string name=\"flutter.$name\">${esc(list + "[" + items.joinToString(",") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" } + "]")}</string>"

    private val thread = "https://may.2chan.net/27/res/325098.htm"
    private val xml = buildString {
        append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n")
        append("<long name=\"flutter.mainColor\" value=\"4286578688\" />\n")
        append("<long name=\"flutter.themeMode\" value=\"1\" />\n")
        append("<boolean name=\"flutter.useGesture\" value=\"false\" />\n")
        append("<string name=\"flutter.privacySure\">VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu0.5</string>\n")
        append("<long name=\"flutter.Gesture12\" value=\"11\" />\n")
        append("<long name=\"flutter.kakikomiColor\" value=\"4294198070\" />\n")
        append("<string name=\"flutter.deleteKey\">abcd1234</string>\n")
        append(listXml("catTBOrderName", listOf("\"検索\"", "\"ドロワーを開く\"")))
        append(listXml("catTBOrderBool", listOf("true", "false")))
        append(listXml("itaList", listOf("""{"name":"ねこ","url":"https://may.2chan.net/27/","favorite":true}""")))
        append(listXml("rirekiUrlList", listOf(thread)))
        append(listXml("tabs", listOf(thread)))
        append(listXml("hihyouji", listOf("http://may.2chan.net/27/res/1.htm")))
        append(listXml("NGList", listOf("""{"text":"荒らし","type":0,"isReg":false,"isActive":true,"ita":null}""")))
        append("<string name=\"flutter.$thread\">${esc("""{"url":"$thread","title":"ねこスレ","resuSuu":3,"resNo":325098,"kokomade":2,"protected":true,"sureOti":false,"resuList":[{"no":325098,"marked":1,"fileThumbUrl":"https://may.2chan.net/27/thumb/1s.jpg"},{"no":325099},{"no":325100},{"no":325101}],"kakikomiData":{"comment":"書きかけ"}}""")}</string>\n")
        append("</map>\n")
    }

    @Test
    fun anEncryptedBackupIsDecryptedAndReadBackAsTheOriginalWroteIt() {
        val key = ByteArray(32) { it.toByte() }
        val iv = ByteArray(16) { (it * 3).toByte() }
        val plain = xml.encodeToByteArray()
        val pad = 16 - plain.size % 16
        val encrypted = FtutterAes(key).ctr(plain + ByteArray(pad) { pad.toByte() }, iv)
        assertEquals(xml, ftutterDecryptBackup(encrypted, key, iv))
        // 鍵が違えば詰め物か中身の検査で断る。
        assertNull(ftutterDecryptBackup(encrypted, ByteArray(32), iv))
    }

    @Test
    fun theSettingsListsAndThreadsAreTranslatedAndAdded() {
        val prefs = parseFtutterPreferencesXml(xml)
        val existingBoard = BoardSummary(id = "img", name = "二次元裏", category = "", url = "https://img.2chan.net/b/futaba.php", description = "")
        val plan = ftutterBackupPlan(prefs, listOf(existingBoard), existingNg = emptyList(), existingPreferences = emptyMap(), now = 1_000)
        val p = plan.preferences
        assertEquals("4286578688", p[FtutterKeys.MAIN_COLOR])
        assertEquals(FtutterBrightness.DARK, parseFtutterDesign(p).brightness)
        assertEquals("false", p[FtutterKeys.USE_GESTURE])
        assertEquals("0.5", p[FtutterKeys.PRIVACY_THREAD])
        assertEquals("11", p[FtutterKeys.gesture("12")])
        assertEquals("4294198070", p[FtutterKeys.colorOverride("kakikomi")])
        assertEquals("abcd1234", p[FtutterKeys.DELETE_KEY])
        // ツールバーの並びは、原作の表示名から このモードの名前へ。
        val toolbar = parseFtutterCatalogToolbar(p)
        assertEquals(listOf(FtutterCatalogTool.SEARCH, FtutterCatalogTool.DRAWER), toolbar.take(2).map { it.tool })
        assertEquals(false, toolbar[1].enabled)
        // 板・履歴・既読・保護・タブ・非表示・NG・マーク・下書き。
        assertEquals(1, plan.addedBoards)
        assertTrue(plan.boards.first { it.url.contains("may.2chan.net/27") }.pinned)
        assertEquals(1, plan.history.size)
        assertEquals("ねこスレ", plan.history[0].title)
        assertEquals(4, plan.history[0].replyCount)
        assertEquals(2, parseFtutterReadCounts(p[FtutterKeys.READ_COUNTS])[thread])
        assertEquals(setOf(thread), parseFtutterProtected(p))
        assertEquals(listOf(thread), parseFtutterTabs(p).urls)
        assertEquals(listOf("https://may.2chan.net/27/res/1.htm"), parseFtutterHiddenThreads(p[FtutterKeys.HIDDEN_THREADS]))
        assertEquals(listOf(FtutterNgRule("荒らし", 0)), parseFtutterNgList(p[FtutterKeys.NG_LIST]))
        assertEquals(listOf(thread to "325098"), plan.marks)
        assertNotNull(parseFtutterDrafts(p[FtutterKeys.DRAFTS]).values.firstOrNull { it.comment == "書きかけ" })
        assertTrue(plan.summary.contains("履歴 1件"))
    }

    @Test
    fun aSecondImportDoesNotDuplicateRules() {
        val prefs = parseFtutterPreferencesXml(xml)
        val plan = ftutterBackupPlan(prefs, emptyList(), existingNg = listOf(FtutterNgRule("荒らし", 0)), existingPreferences = emptyMap(), now = 0)
        assertEquals(0, plan.ngCount)
        assertNull(plan.preferences[FtutterKeys.NG_LIST])
    }

    @Test
    fun importKeepsTheSelectedBoardTabFavoritesAndPerBoardStatistics() {
        val board = BoardSummary("neko", "ねこ", "", "https://may.2chan.net/27/futaba.php", "")
        val original = parseFtutterPreferencesXml(xml) + mapOf(
            "openingCatalogUrl" to FtutterPrefValue.Text("http://may.2chan.net/27/"),
            "opening" to FtutterPrefValue.Text(thread),
            "useHttps" to FtutterPrefValue.Bool(false),
            "useChromeCustomTab" to FtutterPrefValue.Bool(false),
            "itaList" to FtutterPrefValue.Texts(listOf(
                """{"name":"ねこ","url":"https://may.2chan.net/27/","favorite":true,"sureOpenCount":12,"suretateCount":2,"resuCount":3,"soudaneCount":4}""",
                """{"name":"二次元裏","url":"https://img.2chan.net/b/","sureOpenCount":5,"suretateCount":6,"resuCount":7,"soudaneCount":8}"""
            ))
        )
        val plan = ftutterBackupPlan(original, listOf(board), emptyList(), emptyMap(), 0)
        assertTrue(plan.boards.first { it.id == board.id }.pinned)
        assertEquals(board.id, plan.preferences[FtutterKeys.LAST_BOARD])
        assertEquals(thread, plan.preferences[FtutterKeys.OPENING])
        assertEquals(FtutterStats(17, 8, 10, 12), parseFtutterStats(plan.preferences))
        assertNull(plan.preferences[FtutterKeys.USE_HTTPS])
        assertEquals("false", plan.preferences[FtutterKeys.PREFIX + "useChromeCustomTab"])
        val repeated = ftutterBackupPlan(original, plan.boards, emptyList(), plan.preferences, 0)
        assertEquals(parseFtutterStats(plan.preferences), parseFtutterStats(repeated.preferences))
    }

    @Test
    fun importExplainsRequiredFilesAndPermissionsAndImportsTheIndividualColors() {
        val original = parseFtutterPreferencesXml(xml) + mapOf(
            "userFontPath" to FtutterPrefValue.Text("/original/font.ttf"),
            "savePath" to FtutterPrefValue.Text("content://original/tree/1"),
            "savePathList" to FtutterPrefValue.Texts(listOf("content://original/tree/1")),
            "gestureColorOverride" to FtutterPrefValue.Number("4278190080"),
            thread to FtutterPrefValue.Text("""{"title":"下書き","kakikomiData":{"comment":"本文","upFile":"/original/image.png"}}""")
        )
        val plan = ftutterBackupPlan(original, emptyList(), emptyList(), emptyMap(), 0)
        assertEquals(3, plan.limitations.size)
        assertTrue(plan.summary.contains("引き継ぎの制限"))
        assertTrue(plan.limitations.any { it.contains("フォント") })
        assertTrue(plan.limitations.any { it.contains("保存先") })
        assertEquals("4278190080", plan.preferences[FtutterKeys.colorOverride("gesture")])
        assertEquals("/original/font.ttf", plan.preferences[FtutterKeys.PREFIX + "import.userFontPath"])
        assertTrue(plan.limitations.any { it.contains("添付ファイル") })
        assertTrue(parseFtutterDrafts(plan.preferences[FtutterKeys.DRAFTS]).values.any { it.comment == "本文" && it.sourceAttachmentPath == "/original/image.png" })
    }

    /** L132: the confirmation counts the imported tabs apart from the current ones and says how many the limit closes. */
    @Test
    fun theTabSummarySeparatesImportedAndCurrentTabsAndCountsWhatTheLimitCloses() {
        fun url(n: Int) = "https://may.2chan.net/b/res/$n.htm"
        val imported = listOf(url(1), url(2), url(3))
        val current = listOf(url(11), url(12), url(13))
        val existing = mapOf(FtutterKeys.TABS to encodeFtutterList(current), FtutterKeys.HISTORY_LIMIT to "4")
        val plan = ftutterBackupPlan(mapOf("tabs" to FtutterPrefValue.Texts(imported)), emptyList(), emptyList(), existing, 0,
            existingHistoryOrder = listOf(url(13), url(12), url(11)))
        assertTrue(plan.summary.contains("タブ：取り込み 3件（今のタブ 3件と合わせて 6件。履歴の保存数 4 を超える古いタブ 2件は閉じます）"), plan.summary)
        val kept = decodeFtutterList(plan.preferences[FtutterKeys.TABS]).orEmpty()
        assertEquals(imported + url(13), kept, "The imported tabs and the newest current tab stay")
        val roomy = ftutterBackupPlan(mapOf("tabs" to FtutterPrefValue.Texts(imported)), emptyList(), emptyList(),
            existing + (FtutterKeys.HISTORY_LIMIT to "200"), 0)
        assertTrue(roomy.summary.contains("タブ：取り込み 3件（今のタブ 3件と合わせて 6件）"), roomy.summary)
    }

    /** L19: the import remembers what was chosen then, so the notice about the original ends once another is chosen. */
    @Test
    fun theImportRemembersTheChoicesItsNoticesWaitFor() {
        val original = parseFtutterPreferencesXml(xml) + mapOf(
            "userFontPath" to FtutterPrefValue.Text("/original/font.ttf"),
            "savePath" to FtutterPrefValue.Text("content://original/tree/1")
        )
        val existing = mapOf(FTUTTER_SHARED_DESTINATION_KEY to "content://current/tree/9",
            com.valoser.futacha.shared.ui.futaber.FUTABER_CUSTOM_FONT_PREFERENCE_KEY to "デフォルト")
        val plan = ftutterBackupPlan(original, emptyList(), emptyList(), existing, 0)
        assertEquals("content://current/tree/9", plan.preferences[FTUTTER_IMPORT_SAVE_PATH_CHOSEN])
        assertEquals("デフォルト", plan.preferences[FTUTTER_IMPORT_FONT_CHOSEN])
        assertTrue(ftutterImportNoticePending("content://original/tree/1", "content://current/tree/9", "content://current/tree/9"))
        assertFalse(ftutterImportNoticePending("content://original/tree/1", "content://current/tree/9", "content://new/tree/2"))
        assertTrue(ftutterImportNoticePending("/original/font.ttf", null, "anything"), "An earlier import keeps its notice until dismissed")
        assertFalse(ftutterImportNoticePending("", null, null))
    }
}
