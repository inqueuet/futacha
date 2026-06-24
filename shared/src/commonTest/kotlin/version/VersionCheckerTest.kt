package com.valoser.futacha.shared.version

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionCheckerTest {
    @Test
    fun sanitizeGitHubReleaseTextForDisplay_removesMarkdownSyntax() {
        val text = """
            # v1.2.0

            - [設定画面](https://example.com/settings)を整理しました
            - **本文サイズ** と `レス画像サイズ` を追加しました
            1. 詳細は <https://example.com/releases> を確認してください
            > 引用風の行
            ---
            https://example.com/raw-url
        """.trimIndent()

        assertEquals(
            """
            v1.2.0

            ・設定画面を整理しました
            ・本文サイズ と レス画像サイズ を追加しました
            ・詳細は を確認してください
            引用風の行
            """.trimIndent(),
            sanitizeGitHubReleaseTextForDisplay(text)
        )
    }

    @Test
    fun buildUpdateMessage_includesSanitizedReleaseBodyWithoutAdNotice() {
        val message = buildUpdateMessage(
            current = "1.0.0",
            latest = "1.1.0",
            releaseName = "Release **1.1.0**",
            releaseBody = "- [改善](https://example.com)しました"
        )

        assertTrue(message.contains("現在: v1.0.0"))
        assertTrue(message.contains("最新: v1.1.0"))
        assertTrue(message.contains("Release 1.1.0"))
        assertTrue(message.contains("・改善しました"))
        assertFalse(message.contains("https://"))
        assertFalse(message.contains("広告"))
    }
}
