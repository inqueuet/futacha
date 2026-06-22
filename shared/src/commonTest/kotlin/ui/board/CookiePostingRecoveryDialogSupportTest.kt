package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals

class CookiePostingRecoveryDialogSupportTest {

    @Test
    fun buildCookiePostingRecoveryGuidance_returnsMissingCookieMessageWhenNothingSaved() {
        assertEquals(
            CookiePostingRecoveryGuidance(
                title = "書き込み用Cookieがありません",
                message = "まだ Cookie が保存されていません。書き込みを試すと、失敗しても Cookie だけ保存される場合があります。Cookie 画面で保存状態を確認し、保存されていれば Cookie を削除せずにもう一度投稿してください。"
            ),
            buildCookiePostingRecoveryGuidance(
                hasAnyCookies = false,
                hasPostingCookiesForBoard = false
            )
        )
    }

    @Test
    fun buildCookiePostingRecoveryGuidance_returnsBoardSpecificMessageWhenPostingCookieMissing() {
        assertEquals(
            CookiePostingRecoveryGuidance(
                title = "この板の書き込み用Cookieがありません",
                message = "Cookie はありますが、この板で使う書き込み用 Cookie が見つかりません。この板で一度書き込みを試すと、失敗しても Cookie だけ保存される場合があります。保存されていれば Cookie を削除せずにもう一度投稿してください。"
            ),
            buildCookiePostingRecoveryGuidance(
                hasAnyCookies = true,
                hasPostingCookiesForBoard = false
            )
        )
    }

    @Test
    fun buildCookiePostingRecoveryGuidance_returnsResetMessageWhenPostingCookieExists() {
        assertEquals(
            CookiePostingRecoveryGuidance(
                title = "もう一度投稿してください",
                message = "投稿用 Cookie は保存されています。Cookie を削除せずにもう一度投稿してください。サーバーが残り秒数を返した場合は、その時間まで待ってから再試行してください。"
            ),
            buildCookiePostingRecoveryGuidance(
                hasAnyCookies = true,
                hasPostingCookiesForBoard = true
            )
        )
    }
}
