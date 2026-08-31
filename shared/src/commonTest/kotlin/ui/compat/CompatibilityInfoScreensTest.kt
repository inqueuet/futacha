package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatibilityInfoScreensTest {
    @Test
    fun futachaChangeLogContainsVersionsAndChangesOnly() {
        val versions = FUTACHA_CHANGE_LOG_ENTRIES.map(FutachaChangeLogEntry::version)

        assertEquals(
            listOf(
                "10.1", "10.0", "9.9", "9.8", "9.7", "9.6", "9.5", "9.4", "9.3", "9.1", "9.0", "8.9", "8.8", "8.6", "8.5", "8.4", "8.2", "8.0",
                "7.8", "7.7", "7.5", "7.2", "7.1", "6.7", "6.6", "6.3", "6.2", "6.1", "6.0",
                "5.9", "5.8", "5.6", "5.4", "5.3", "5.1", "5.0", "4.9", "4.7", "4.6", "4.4",
                "4.2", "4.1", "4.0", "3.9", "3.8", "3.7", "3.6", "3.4", "3.3", "3.2", "3.0",
                "2.8", "2.7", "2.6", "2.5", "2.4", "2.3", "2.2", "2.1", "2.0", "1.9", "1.8",
                "1.7", "1.6", "1.5", "1.4", "1.3", "1.2", "1.1", "1.0", "0.9", "0.6", "0.5",
                "0.4", "0.3", "0.1"
            ),
            versions
        )
        assertTrue("<li>" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("ライセンス表示を、アプリで実際に使用している内容に合わせて修正します。" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("1000レス規模のスレでも画面外の画像を一度に取得せず" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("画像一覧で、画像・動画を複数選択し、ZIPまたはフォルダへ一括保存" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("板一覧から複数の板をまとめて追加" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("画面内の戻るボタンで移動" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("標準アイコンを、新しいデザインへ変更" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("取得先の入力なしで利用" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("取得できる全板を正しく登録" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("あぷ小画像の表示が遅い問題" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("書き込み画面のメニューを適切な大きさ" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("引用内の画像から不要なサムネイル" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("ファイル名末尾に付く「[見る]」を除去" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("読みやすい文字サイズと行間" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("PNG/APNGの判定結果を再利用" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("待機後にCookieの削除と再発行が繰り返される問題" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("サーバーからの理由も表示" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("GitHub（https://github.com/inqueuet/futacha）で再公開" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("修正前から端末に残る過去ログキャッシュ" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("削除・隔離通知だけを赤く" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("横長のあぷ小サムネイルを正しい縦横比" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("過去ログ側のHTML形式によっては残る問題" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue("オフラインコピー、保存済みHTMLのどの表示方法でも" in FUTACHA_CHANGE_LOG_HTML)
        assertFalse(Regex("""20[0-9]{2}-[0-9]{2}-[0-9]{2}""").containsMatchIn(FUTACHA_CHANGE_LOG_HTML))
        assertFalse("build " in FUTACHA_CHANGE_LOG_HTML)
        assertFalse("開発中" in FUTACHA_CHANGE_LOG_HTML)
        assertFalse("開発初期の仮版数" in FUTACHA_CHANGE_LOG_HTML)
        assertFalse("build.gradle上に存在しない版数" in FUTACHA_CHANGE_LOG_HTML)
        assertTrue(FUTACHA_CHANGE_LOG_ENTRIES.all { it.changes.isNotEmpty() })
        assertTrue(
            FUTACHA_CHANGE_LOG_ENTRIES.first().changes.first().startsWith("過去ログ本文・引用")
        )
    }

    @Test
    fun licensesDescribeFutachaDependenciesInsteadOfTheReferenceApplication() {
        assertEquals(
            listOf(
                "futacha-open-source-notices",
                "google-sdk-terms",
                "apache-license-2.0"
            ),
            FUTACHA_LICENSE_ASSETS.map { it.id }
        )
        val allText = FUTACHA_LICENSE_ASSETS.joinToString("\n", transform = FutachaLicenseAsset::text)
        listOf("Kotlin", "Compose Multiplatform", "AndroidX", "Ktor", "Coil", "OkHttp", "APNG4Android")
            .forEach { assertTrue(it in allText, "$it must be attributed") }
        listOf("FFmpeg", "Picasso", "DragSortListView", "TouchGallery", "Volley", "jsoup")
            .forEach { assertFalse(it in allText, "$it is not a Futacha dependency") }
        assertTrue("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION" in allText)
        assertTrue("ML Kit GenAI" in allText)
    }

    @Test
    fun changeLogAutoOpenDecisionMatchesVersionGate() {
        assertTrue(shouldOpenCompatChangeLog(null, "8.9"))
        assertTrue(shouldOpenCompatChangeLog("8.8", "8.9"))
        assertEquals(false, shouldOpenCompatChangeLog("8.9", "8.9"))
        assertEquals(false, shouldOpenCompatChangeLog(null, ""))
    }
}
