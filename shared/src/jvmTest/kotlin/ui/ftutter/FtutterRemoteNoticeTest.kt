package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.network.NetworkException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FtutterRemoteNoticeTest {
    @Test
    fun delAndDeletionShowTheServersAnswerLikeTheOriginalUnlessTheLegacySwitchIsOn() {
        assertEquals("<html><body>ok</body></html>", ftutterRemoteSuccessNotice(" <html><body>ok</body></html>\n", false, "削除しました"))
        assertEquals("削除しました", ftutterRemoteSuccessNotice("<html><body>ok</body></html>", true, "削除しました"))
        val refused = IllegalStateException("wrapped", NetworkException("本人削除に失敗しました: 削除キーが違います",
            serverBody = "<html><body>削除キーが違います</body></html>"))
        assertEquals("<html><body>削除キーが違います</body></html>", ftutterRemoteFailureNotice(refused, false))
        assertEquals("x", ftutterRemoteFailureNotice(NetworkException("m (HTTP 500: x)", statusCode = 500, serverBody = "x"), false))
        assertNull(ftutterRemoteFailureNotice(refused, true))
        // No answer (unreachable): our own reason.
        assertNull(ftutterRemoteFailureNotice(NetworkException("Failed to delete post: timeout"), false))
    }

    @Test
    fun aFailedSoudaneSaysOnlyFailedLikeTheOriginal() {
        assertEquals("失敗しました", ftutterSoudaneFailureNotice(FtutterSaidaneNotCountedException(), false))
        assertEquals("失敗しました", ftutterSoudaneFailureNotice(NetworkException("そうだね投票に失敗しました (HTTP 500: err)", statusCode = 500), false))
        assertNull(ftutterSoudaneFailureNotice(NetworkException("Failed to vote saidane: offline"), false))
        assertNull(ftutterSoudaneFailureNotice(NetworkException("x", statusCode = 500), true))
    }
}
