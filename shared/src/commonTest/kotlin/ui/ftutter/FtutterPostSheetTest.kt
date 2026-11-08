package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.network.HttpBoardApiPostResponseMode
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.network.resolveHttpBoardApiPostResponseOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-B3：書き込みの結果をサーバーの本文のままトーストで出す・添付の形式・端末情報・削除キー。 */
class FtutterPostSheetTest {
    private fun refusal(body: String, mode: HttpBoardApiPostResponseMode = HttpBoardApiPostResponseMode.REPLY): Throwable =
        assertFailsWith<NetworkException> { resolveHttpBoardApiPostResponseOrThrow(mode, body, "test") }

    @Test
    fun anIpRefusalShowsTheServersHtmlAndThenTheOriginalsFollowUp() {
        val body = "<html><body>あなたのipアドレスからは投稿できません</body></html>"
        // 送信の処理は、共有の文の失敗をさらに包む（sendPost）。元の本文はその下からたどる。
        val wrapped = IllegalStateException("返信の送信に失敗しました", refusal(body))
        assertEquals(FtutterPostToast(body, FTUTTER_IP_REFUSAL_FOLLOW_UP), ftutterPostFailureToast(wrapped))
    }

    @Test
    fun aJsonErrorAndARegulationAreShownAsTheyCame() {
        val json = """{"status":"error","message":"本文がありません"}"""
        assertEquals(FtutterPostToast(json), ftutterPostFailureToast(refusal(json)))
        val kisei = "<html><body>書き込み規制中です</body></html>"
        assertEquals(FtutterPostToast(kisei), ftutterPostFailureToast(refusal(kisei, HttpBoardApiPostResponseMode.CREATE_THREAD)))
    }

    @Test
    fun anEmptyAnswerIsAnEmptyToast() {
        assertEquals(FtutterPostToast(""), ftutterPostFailureToast(refusal("")))
    }

    @Test
    fun aStopBeforeSendingShowsOurReason() {
        val stopped = IllegalStateException("投稿フォームを確認できませんでした")
        assertEquals(FtutterPostToast("投稿フォームを確認できませんでした"), ftutterPostFailureToast(stopped))
        assertNull(ftutterServerBody(stopped))
    }

    @Test
    fun theOtherModesStillSeeTheSharedMessage() {
        val failure = refusal("<html><body>あなたのipアドレスからは投稿できません</body></html>")
        assertTrue(failure.message!!.startsWith("返信に失敗しました"))
    }

    @Test
    fun onlyTheOriginalsFormatsCanBeAttached() {
        listOf("a.jpg", "b.JPEG", "c.png", "d.gif", "e.webp", "f.mp4", "g.webm").forEach { assertTrue(ftutterAttachmentExtensionAllowed(it), it) }
        listOf("a.heic", "b.mov", "c.txt", "noext").forEach { assertFalse(ftutterAttachmentExtensionAllowed(it), it) }
    }

    @Test
    fun theDeviceInfoReadsLikeTheOriginals() {
        assertEquals(
            "ふたちゃ ver13.7/Android/Android SDK built for arm64/15",
            ftutterDeviceInfoText("13.7", "ふたば＠アプリ としあき(仮) 13.7 google/Android SDK built for arm64/15")
        )
        assertEquals(
            "ふたちゃ ver13.7/iOS/iPhone/18.1",
            ftutterDeviceInfoText("13.7", "ふたば＠アプリ としあき(仮) 13.7 Apple/iPhone/iOS 18.1")
        )
    }

    @Test
    fun theDeleteKeyCountsWhatIsLeft() {
        assertEquals("残り 0 文字（半角相当）", ftutterDeleteKeyRemainingLabel("qi5Qwbrw"))
        assertEquals("残り 5 文字（半角相当）", ftutterDeleteKeyRemainingLabel("abc"))
        assertEquals(8, ftutterGenerateDeleteKey().length)
    }

    @Test
    fun theLegacyDialogSwitchIsOffByDefault() {
        assertFalse(ftutterLegacyPostDialog(emptyMap()))
        assertTrue(ftutterLegacyPostDialog(mapOf(FTUTTER_LEGACY_POST_DIALOG_KEY to "true")))
    }
}
