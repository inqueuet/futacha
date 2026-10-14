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
            .map { File(it, "build/fututter-research-2026-10-13/round2/private-export.ftt") }
            .firstOrNull { it.isFile } ?: return
        val xml = ftutterDecryptBackup(file.readBytes())
        assertNotNull(xml, "復号できませんでした")
        val prefs = parseFtutterPreferencesXml(xml)
        assertTrue(prefs.containsKey("itaList"), "板の一覧がありません")
        val plan = ftutterBackupPlan(prefs, emptyList(), emptyList(), emptyMap(), now = 0)
        assertTrue(plan.addedBoards >= 1, "板を読み替えられませんでした: ${plan.summary}")
        println("ftutter real backup: ${plan.summary}")
    }
}
