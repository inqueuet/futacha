package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals

class CookiePostingRecoveryDialogSupportTest {

    @Test
    fun buildCookiePostingRecoveryGuidance_returnsMissingCookieMessageWhenNothingSaved() {
        assertEquals(
            CookiePostingRecoveryGuidance(
                title = "書き込み用Cookieがありません",
                message = "まだ Cookie が保存されていません。書き込み可能な回線で一度書き込みに成功して、Cookie を作成してから再試行してください。必要なら Cookie 画面で現在の保存状態を確認できます。"
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
                message = "Cookie はありますが、この板で使う書き込み用 Cookie が見つかりません。書き込み可能な回線で一度書き込みに成功して、この板の Cookie を生成してから再試行してください。必要なら Cookie 画面で削除や確認ができます。"
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
                title = "Cookieの再生成を試してください",
                message = "保存済みの Cookie が原因で書き込みに失敗している可能性があります。Cookie を初期化してから、書き込み可能な回線で一度書き込みに成功させ、新しい Cookie を再生成してください。"
            ),
            buildCookiePostingRecoveryGuidance(
                hasAnyCookies = true,
                hasPostingCookiesForBoard = true
            )
        )
    }
}
