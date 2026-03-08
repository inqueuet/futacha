package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogScreenSupportTest {
    @Test
    fun nextCatalogRequestGeneration_incrementsByOne() {
        assertEquals(6L, nextCatalogRequestGeneration(5L))
    }

    @Test
    fun shouldApplyCatalogRequestResult_requiresActiveAndMatchingGeneration() {
        assertTrue(shouldApplyCatalogRequestResult(isActive = true, currentGeneration = 3L, requestGeneration = 3L))
        assertFalse(shouldApplyCatalogRequestResult(isActive = false, currentGeneration = 3L, requestGeneration = 3L))
        assertFalse(shouldApplyCatalogRequestResult(isActive = true, currentGeneration = 4L, requestGeneration = 3L))
    }

    @Test
    fun shouldFinalizeCatalogRefresh_requiresMatchingJobAndGeneration() {
        assertTrue(shouldFinalizeCatalogRefresh(isSameRunningJob = true, currentGeneration = 2L, requestGeneration = 2L))
        assertFalse(shouldFinalizeCatalogRefresh(isSameRunningJob = false, currentGeneration = 2L, requestGeneration = 2L))
        assertFalse(shouldFinalizeCatalogRefresh(isSameRunningJob = true, currentGeneration = 3L, requestGeneration = 2L))
    }

    @Test
    fun buildCatalogRefreshFailureMessage_matchesUiCopy() {
        assertEquals("更新に失敗しました", buildCatalogRefreshFailureMessage())
    }

    @Test
    fun createThreadHelpers_matchUiRules() {
        assertTrue(canSubmitCreateThread(title = "件名", comment = ""))
        assertTrue(canSubmitCreateThread(title = "", comment = "本文"))
        assertFalse(canSubmitCreateThread(title = "", comment = ""))
        assertEquals("板が選択されていません", buildCreateThreadBoardMissingMessage())
        assertEquals(
            "スレッドを作成しました。カタログ更新で確認してください",
            buildCreateThreadSuccessMessage(null)
        )
        assertEquals(
            "スレッドを作成しました (ID: 12345)",
            buildCreateThreadSuccessMessage("12345")
        )
        assertEquals(
            "スレッド作成に失敗しました: boom",
            buildCreateThreadFailureMessage(IllegalStateException("boom"))
        )
        assertEquals(
            "スレッド作成に失敗しました: 不明なエラー",
            buildCreateThreadFailureMessage(IllegalStateException())
        )
    }

    @Test
    fun buildCatalogExternalAppUrl_usesCatalogUrlRules() {
        assertEquals(
            "https://may.2chan.net/b/futaba.php?mode=cat",
            buildCatalogExternalAppUrl("https://may.2chan.net/b/futaba.php", com.valoser.futacha.shared.model.CatalogMode.Catalog)
        )
        assertEquals(
            "https://may.2chan.net/b/futaba.php?mode=cat&sort=1",
            buildCatalogExternalAppUrl("https://may.2chan.net/b/futaba.php", com.valoser.futacha.shared.model.CatalogMode.New)
        )
        assertEquals(
            "https://may.2chan.net/b/futaba.php?mode=cat",
            buildCatalogExternalAppUrl("https://may.2chan.net/b/futaba.php", com.valoser.futacha.shared.model.CatalogMode.WatchWords)
        )
    }

    @Test
    fun buildCatalogLoadErrorMessage_mapsKnownCases() {
        assertEquals(
            "タイムアウト: サーバーが応答しません",
            buildCatalogLoadErrorMessage(IllegalStateException("request timeout"))
        )
        assertEquals(
            "板が見つかりません (404)",
            buildCatalogLoadErrorMessage(IllegalStateException("HTTP 404"))
        )
        assertEquals(
            "サーバーエラー (500)",
            buildCatalogLoadErrorMessage(IllegalStateException("HTTP 500"))
        )
        assertEquals(
            "ネットワークエラー: HTTP error: refused",
            buildCatalogLoadErrorMessage(IllegalStateException("HTTP error: refused"))
        )
        assertEquals(
            "データサイズが大きすぎます",
            buildCatalogLoadErrorMessage(IllegalStateException("response exceeds maximum size"))
        )
        assertEquals(
            "カタログを読み込めませんでした: boom",
            buildCatalogLoadErrorMessage(IllegalStateException("boom"))
        )
        assertEquals(
            "カタログを読み込めませんでした: 不明なエラー",
            buildCatalogLoadErrorMessage(IllegalStateException())
        )
    }
}
