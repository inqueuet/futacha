package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.BoardSummary
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
