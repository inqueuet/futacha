package com.valoser.futacha.shared.ui.ftutter

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 原作（ふたったー 3.1.1）が実際に書き出した `.ftt`（調査用のエミュレーターで作ったもの。Gitには入れない）を、埋め込んだ鍵で
 * 復号して読めるかを確かめる。ファイルが無い環境では何もしない。
 */
class FtutterRealBackupTest {
    @Test
    fun theOriginalAppsBackupIsDecryptedAndTranslated() {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .flatMap { root -> sequenceOf(
                File(root, "build/ftutter-restore-investigation/original-export-with-added-settings.ftt"),
                File(root, "build/ftutter-restore-investigation/original-export.ftt"),
                File(root, "build/fututter-research-2026-10-13/round2/private-export.ftt")
            ) }
            .firstOrNull { it.isFile } ?: return
        val xml = ftutterDecryptBackup(file.readBytes())
        assertNotNull(xml, "復号できませんでした")
        val prefs = parseFtutterPreferencesXml(xml)
        assertTrue(prefs.containsKey("itaList"), "板の一覧がありません")
        val plan = ftutterBackupPlan(prefs, emptyList(), emptyList(), emptyMap(), now = 0)
        assertTrue(plan.addedBoards >= 1, "板を読み替えられませんでした: ${plan.summary}")
        (prefs["opening"] as? FtutterPrefValue.Text)?.let { opening ->
            if (ftutterCanonicalThreadUrl(opening.value) in parseFtutterTabs(plan.preferences).urls) {
                kotlin.test.assertEquals(ftutterCanonicalThreadUrl(opening.value), plan.preferences[FtutterKeys.OPENING])
            }
        }
        var scalarCount = 0
        FTUTTER_BACKUP_SAME_NAME_SETTINGS.forEach { target ->
            val original = prefs[target.removePrefix(FtutterKeys.PREFIX)] ?: return@forEach
            val expected = when (original) {
                is FtutterPrefValue.Text -> original.value
                is FtutterPrefValue.Number -> original.value
                is FtutterPrefValue.Bool -> original.value.toString()
                is FtutterPrefValue.Texts -> return@forEach
            }
            assertTrue(expected == plan.preferences[target], "設定の読み替え: $target")
            scalarCount++
        }
        var colorCount = 0
        listOf("background", "text", "toolBar", "onToolBar", "onToolBarActive", "popUp", "highlight", "dividerCard",
            "onDividerCard", "inTab", "gesture", "scrollBar", "indicator", "outline", "kidoku", "midoku", "onMidoku", "headerText", "resuSuu", "onResuSuu",
            "pickUp", "onPickUp", "kakikomi", "henshin").forEach { name ->
            val original = prefs["${name}ColorOverride"] ?: prefs["${name}Color"] ?: return@forEach
            kotlin.test.assertEquals((original as FtutterPrefValue.Number).value, plan.preferences[FtutterKeys.colorOverride(name)], name)
            colorCount++
        }
        println("ftutter real backup: $scalarCount scalar settings, $colorCount custom colours checked")
        println("ftutter real backup: ${plan.summary}")
    }
}
