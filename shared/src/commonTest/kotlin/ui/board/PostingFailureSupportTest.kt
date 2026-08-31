package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostingFailureSupportTest {
    @Test
    fun failureMessageDoesNotDuplicateCanonicalOrEquivalentPrefixes() {
        assertEquals(
            "返信の送信に失敗しました: 接続失敗",
            buildPostingAwareFailureMessage(
                "返信の送信に失敗しました",
                IllegalStateException("返信の送信に失敗しました: 接続失敗")
            )
        )
        assertEquals(
            "返信に失敗しました: Cookieを確認してください",
            buildPostingAwareFailureMessage(
                "返信の送信に失敗しました",
                IllegalStateException("返信に失敗しました: Cookieを確認してください")
            )
        )
    }

    @Test
    fun failureMessageUsesFallbackAndBoundsUntrustedServerDetail() {
        assertEquals(
            "スレッド作成に失敗しました: 詳細なし",
            buildPostingAwareFailureMessage(
                "スレッド作成に失敗しました",
                IllegalStateException(),
                fallbackDetail = "詳細なし"
            )
        )
        val message = buildPostingAwareFailureMessage("失敗", IllegalStateException("x".repeat(2_000)))
        assertEquals("失敗: ".length + 500, message.length)
    }

    @Test
    fun cookieActionIsOfferedOnlyForExplicitCookieRecoveryFailures() {
        assertTrue(
            shouldOfferCookieManagerForPostingFailure(
                IllegalStateException("Cookieを再取得してから投稿してください")
            )
        )
        assertFalse(shouldOfferCookieManagerForPostingFailure(IllegalStateException("接続がタイムアウトしました")))
        assertFalse(shouldOfferCookieManagerForPostingFailure(IllegalStateException()))
    }

    @Test
    fun inferredWaitAndElapsedWaitMessagesDoNotOfferCookieDeletionAction() {
        assertFalse(
            shouldOfferCookieManagerForPostingFailure(
                IllegalStateException(
                    "返信に失敗しました: あなたのIPからは投稿できません " +
                        "あと約58分投稿できない可能性があります。" +
                        "1時間基準の推定です。保存済み情報は削除せず、そのままお待ちください"
                )
            )
        )
        assertFalse(
            shouldOfferCookieManagerForPostingFailure(
                IllegalStateException(
                    "返信に失敗しました: あなたのIPからは投稿できません " +
                        "1時間の推定待機時間を過ぎましたが、サーバーの制限が続いています。" +
                        "保存済み情報は削除せず、サーバー応答を確認してください"
                )
            )
        )
    }
}
