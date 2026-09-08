@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.valoser.futacha

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.compat.AndroidLauncherAliasManager
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.AppIconVariant
import com.valoser.futacha.shared.model.PostDeletionKind
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UserFeedbackPresentationInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "feedback_${System.nanoTime()}.db"
    private val store = AndroidCompatibilityStore(context, databaseName = databaseName)
    private val url = "https://may.2chan.net/b/res/100.htm"
    private val tabKey = compatTabKey(url)

    @After fun close() {
        runBlocking { store.closeForTest() }
        context.deleteDatabase(databaseName)
    }

    @Test fun changeLogDefaultShowsVersionHeading() = changeLog("default")
    @Test fun changeLogBlackShowsVersionHeading() = changeLog("black")

    private fun changeLog(theme: String) {
        runBlocking {
            store.initialize()
            store.savePreference("compat.design.designTheme", theme)
        }
        rule.setContent {
            MaterialTheme { CompatibilityApp(store = store, repository = null, appVersion = "feedback", onExitApplication = {}) }
        }
        rule.onNodeWithText("更新履歴").assertIsDisplayed()
        val heading = rule.onNodeWithText("10.7").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        heading.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.size)
        val color = layouts.single().layoutInput.style.color
        if (theme == "black") assertNotEquals(Color.Black, color)
        val pixels = heading.captureToImage().toPixelMap()
        val background = pixels[0, 0]
        assertTrue("Heading must actually paint visible glyphs", (0 until pixels.width).any { x ->
            (0 until pixels.height).any { y -> pixels[x, y] != background }
        })
        screenshot("changelog-after-$theme")
    }

    @Test fun deletedContentVisibleDefaultKeepsEachNoticeRed() = deletion("default", true)
    @Test fun deletedContentVisibleBlackKeepsEachNoticeRed() = deletion("black", true)
    @Test fun deletedContentHiddenKeepsKindsAndSummary() = deletion("black", false)

    private fun prepareThread(theme: String, showDeleted: Boolean) = runBlocking {
        store.initialize()
        store.savePreference("compat.design.designTheme", theme)
        store.savePreference("compat.thread.threadAdminDeleteShow", if (showDeleted) "ON" else "OFF")
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        store.upsertBoard(CompatBoard(boardKey, "検証", boardUrl, boardUrl, 0))
        store.openTab(CompatTab(key = tabKey, canonicalUrl = url, originalUrl = url, boardKey = boardKey,
            boardName = "検証", threadNo = "100", title = "スレ本文", replyCount = 4,
            insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1, snapshotRevision = 1))
        store.saveThreadSnapshot(CompatThreadSnapshot(tabKey = tabKey, revision = 1, fetchedAtEpochMillis = 1,
            deletedNotice = "削除された記事が4件あります.見る",
            posts = listOf(CompatPostSnapshot(0, "100", timestamp = "", messageHtml = "スレ本文")) +
                PostDeletionKind.entries.take(4).mapIndexed { index, kind ->
                    CompatPostSnapshot(index + 1, "${101 + index}", timestamp = "",
                        messageHtml = "<font color=\"#ff0000\">${kind.notice}</font><br>元の本文${index + 1}",
                        isDeleted = kind != PostDeletionKind.ISOLATED, isIsolated = kind == PostDeletionKind.ISOLATED)
                }))
    }

    private fun showThread() {
        rule.setContent {
            MaterialTheme { CompatibilityApp(store = store, repository = null, initialThreadDeepLink = url, onExitApplication = {}) }
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("compat-thread-deletion-summary", useUnmergedTree = true).fetchSemanticsNodes(false).isNotEmpty()
        }
    }

    private fun deletion(theme: String, showDeleted: Boolean) {
        prepareThread(theme, showDeleted)
        showThread()
        val summary = rule.onNodeWithTag("compat-thread-deletion-summary", useUnmergedTree = true).assertIsDisplayed()
        summary.assertTextEquals("削除・隔離されたレス：4件（スレ主：1件／投稿者本人：1件／管理者：1件／隔離：1件）")
        assertTrue(summary.fetchSemanticsNode().boundsInRoot.top >= rule.onNode(hasText("スレ本文") and hasAnyAncestor(hasTestTag("compat-thread-post-100")), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.bottom)
        screenshot("deletions-$theme-${if (showDeleted) "visible" else "hidden"}")
        PostDeletionKind.entries.take(4).forEach { kind ->
            val notice = rule.onNodeWithText(kind.notice, substring = showDeleted, useUnmergedTree = true)
            notice.performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            notice.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val input = layouts.single().layoutInput
            if (showDeleted) {
                assertTrue(input.text.spanStyles.any { it.start == 0 && it.end == kind.notice.length && it.item.color == Color.Red })
                assertNotEquals(Color.Red, input.style.color)
            } else {
                assertEquals(Color.Red, input.style.color)
            }
        }
    }

    @Test fun quickReplyLeavesExactlyOneTrailingNewlineInEditableField() {
        prepareThread("default", true)
        showThread()
        rule.onNode(hasText("スレ本文") and hasAnyAncestor(hasTestTag("compat-thread-post-100")), useUnmergedTree = true).performTouchInput { longClick() }
        rule.onNodeWithText("クイック").performClick()
        val field = rule.onNodeWithTag("compat-post-comment-field").assertIsDisplayed()
        assertEquals(">スレ本文\n", field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        field.performTextInput("返信")
        assertEquals(">スレ本文\n返信", field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }

    @Test fun modernThreadColorsOnlyTheDeletionNoticeRed() {
        val notice = PostDeletionKind.AUTHOR.notice
        rule.setContent {
            MaterialTheme {
                com.valoser.futacha.shared.ui.board.ThreadMessageText(
                    messageHtml = "<font color=\"#ff0000\">$notice</font><br>元の本文",
                    isDeleted = true, quoteReferences = emptyList(), onQuoteClick = {}, onUrlClick = {}
                )
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText(notice, substring = true).fetchSemanticsNodes(false).isNotEmpty() }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(notice, substring = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val input = layouts.single().layoutInput
        assertTrue(input.text.spanStyles.any { it.start == 0 && it.end == notice.length && it.item.color == Color.Red })
        assertNotEquals(Color.Red, input.style.color)
    }

    @Test fun modeSwitchKeepsSameLauncherIconAndMigratesLegacyAlias() {
        val manager = AndroidLauncherAliasManager(context)
        val previous = manager.enabledAliases().singleOrNull()
        val restore = when (previous) {
            AndroidLauncherAliasManager.CLASSIC_ALIAS -> AppIconVariant.Classic
            AndroidLauncherAliasManager.MIDNIGHT_ALIAS -> AppIconVariant.Midnight
            else -> AppIconVariant.Current
        }
        try {
            context.packageManager.setComponentEnabledSetting(
                android.content.ComponentName(context, AndroidLauncherAliasManager.TOSHIAKI_COMPAT_ALIAS),
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
            listOf(AppIconVariant.Current, AppIconVariant.Classic).forEach { variant ->
                manager.reconcile(ExperienceProfile.FUTACHA, variant)
                val before = manager.enabledAliases()
                manager.reconcile(ExperienceProfile.TOSHIAKI_COMPAT, variant)
                assertEquals(before, manager.enabledAliases())
                assertFalse(AndroidLauncherAliasManager.TOSHIAKI_COMPAT_ALIAS in manager.enabledAliases())
                manager.reconcile(ExperienceProfile.FUTACHA, variant)
                assertEquals(before, manager.enabledAliases())
            }
        } finally {
            manager.reconcile(ExperienceProfile.FUTACHA, restore)
        }
    }

    private fun screenshot(name: String) {
        context.openFileOutput("feedback-$name.png", Context.MODE_PRIVATE).use { stream ->
            assertTrue(rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
    }
}
