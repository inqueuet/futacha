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
import com.valoser.futacha.shared.ui.compat.FUTACHA_CHANGE_LOG_ENTRIES
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

    @Test fun changeLogBlackIgnoresThreadTextColorOverride() = changeLog("black", "黒")

    private fun changeLog(theme: String, textOverride: String? = null) {
        runBlocking {
            store.initialize()
            store.savePreference("compat.design.designTheme", theme)
            if (textOverride != null) store.savePreference("compat.design.designTextColor", textOverride)
        }
        rule.setContent {
            MaterialTheme { CompatibilityApp(store = store, repository = null, appVersion = "feedback", onExitApplication = {}) }
        }
        rule.onNodeWithText("更新履歴").assertIsDisplayed()
        val heading = rule.onNodeWithText("10.8").assertIsDisplayed()
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
        screenshot("changelog-after-$theme-${textOverride ?: "normal"}")
        FUTACHA_CHANGE_LOG_ENTRIES.forEachIndexed { index, entry ->
            rule.onNodeWithTag("compat-change-log-content").performScrollToIndex(index)
            val version = rule.onNodeWithText(entry.version, useUnmergedTree = true)
            assertReadableText(version, theme)
            entry.changes.indices.forEach { changeIndex ->
                assertReadableText(rule.onNodeWithTag("compat-change-log-body-${entry.version}-$changeIndex", useUnmergedTree = true), theme)
            }
        }
        rule.onNodeWithText(FUTACHA_CHANGE_LOG_ENTRIES.last().version).assertIsDisplayed()
        screenshot("changelog-oldest-$theme-${textOverride ?: "normal"}")
    }

    private fun assertReadableText(node: SemanticsNodeInteraction, theme: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach {
            assertNotEquals("Text must contrast with the page background: ${it.layoutInput.text}",
                if (theme == "black") Color.Black else Color.White, it.layoutInput.style.color)
        }
    }

    @Test fun blackSelectedSageRemainsVisible() {
        prepareThread("black", true)
        showThread()
        rule.onNode(hasText("スレ本文") and hasAnyAncestor(hasTestTag("compat-thread-post-100")), useUnmergedTree = true).performTouchInput { longClick() }
        rule.onNodeWithText("クイック").performClick()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        val sage = rule.onNodeWithText("sage")
        sage.performScrollTo().performClick()
        screenshot("black-selected-sage")
        assertReadableText(rule.onNode(hasText("sage") and !hasSetTextAction(), useUnmergedTree = true), "black")
    }

    @Test fun blackGallerySaveProgressRemainsVisible() {
        prepareThread("black", true)
        runBlocking {
            val snapshot = store.loadThreadSnapshot(tabKey)!!
            assertTrue(store.saveThreadSnapshot(snapshot.copy(revision = snapshot.revision + 1, posts = snapshot.posts.take(1).map {
                it.copy(imageUrl = "https://example.invalid/feedback.jpg", thumbnailUrl = "https://example.invalid/feedback.jpg")
            })))
        }
        val client = io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine { kotlinx.coroutines.awaitCancellation() })
        try {
            rule.setContent {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, httpClient = client,
                        fileSystem = com.valoser.futacha.shared.util.AndroidFileSystem(context),
                        initialThreadDeepLink = url, onExitApplication = {})
                }
            }
            rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("画像一覧").fetchSemanticsNodes(false).isNotEmpty() }
            rule.onNodeWithContentDescription("画像一覧").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-gallery-item-100").fetchSemanticsNodes(false).isNotEmpty() }
            rule.onNodeWithTag("compat-gallery-item-100").performTouchInput { longClick() }
            rule.onNodeWithText("画像を保存する").performScrollTo().performClick()
            val progress = rule.onNode(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate))
            progress.assertIsDisplayed()
            screenshot("black-gallery-saving")
            val pixels = progress.captureToImage().toPixelMap()
            assertTrue("Saving spinner must paint visible pixels on the black image background", (0 until pixels.width).any { x ->
                (0 until pixels.height).any { y -> pixels[x, y] != Color.Black }
            })
        } finally {
            client.close()
        }
    }

    @Test fun blackSelectionReplyHasOneNewlineAndEditableText() {
        prepareThread("black", true)
        showThread()
        rule.onNode(hasText("スレ本文") and hasAnyAncestor(hasTestTag("compat-thread-post-100")), useUnmergedTree = true).performTouchInput { longClick() }
        screenshot("black-post-menu")
        rule.onNodeWithText("返信").performClick()
        rule.onNode(hasText("スレ本文") and hasAnyAncestor(isDialog())).performClick()
        screenshot("black-quote-selection")
        rule.onNodeWithText("上書き").performClick()
        val field = rule.onNodeWithTag("compat-post-comment-field").assertIsDisplayed()
        assertEquals(">スレ本文\n", field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        field.performTextInput("返信")
        assertEquals(">スレ本文\n返信", field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        screenshot("black-quote-editor")
    }

    @Test fun deletedContentVisibleDefaultKeepsEachNoticeRed() = deletion("default", true)
    @Test fun deletedContentVisibleBlackKeepsEachNoticeRed() = deletion("black", true)
    @Test fun deletedContentHiddenKeepsKindsAndSummary() = deletion("black", false)

    private fun prepareThread(theme: String, showDeleted: Boolean, serverCount: Int = 4, includeDeleted: Boolean = true) = runBlocking {
        store.initialize()
        store.savePreference("compat.design.designTheme", theme)
        store.savePreference("compat.thread.threadAdminDeleteShow", if (showDeleted) "ON" else "OFF")
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        store.upsertBoard(CompatBoard(boardKey, "検証", boardUrl, boardUrl, 0))
        store.openTab(CompatTab(key = tabKey, canonicalUrl = url, originalUrl = url, boardKey = boardKey,
            boardName = "検証", threadNo = "100", title = "スレ本文", replyCount = if (includeDeleted) 4 else 0,
            insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1, snapshotRevision = 1))
        store.saveThreadSnapshot(CompatThreadSnapshot(tabKey = tabKey, revision = 1, fetchedAtEpochMillis = 1,
            deletedNotice = "削除された記事が${serverCount}件あります.見る",
            posts = listOf(CompatPostSnapshot(0, "100", timestamp = "", messageHtml = "スレ本文")) +
                PostDeletionKind.entries.take(if (includeDeleted) 4 else 0).mapIndexed { index, kind ->
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
            rule.onAllNodesWithTag("compat-thread-post-100", useUnmergedTree = true).fetchSemanticsNodes(false).isNotEmpty()
        }
    }

    @Test fun blackServerOnlyDeletionCountsRemainUnknownWhenBodiesAreHidden() {
        prepareThread("black", false, serverCount = 7)
        showThread()
        val summary = rule.onNodeWithTag("compat-thread-deletion-summary", useUnmergedTree = true)
        summary.assertIsDisplayed().assertTextEquals("削除・隔離されたレス：7件（スレ主：1件／投稿者本人：1件／管理者：1件／隔離：1件／種類不明：3件）")
        assertReadableText(summary, "black")
        screenshot("black-deletion-unknown")
    }

    @Test fun blackZeroDeletionsDoNotLeaveAnEmptySummary() {
        prepareThread("black", true, serverCount = 0, includeDeleted = false)
        showThread()
        rule.onNodeWithTag("compat-thread-deletion-summary", useUnmergedTree = true).assertDoesNotExist()
        rule.onNode(hasText("スレ本文") and hasAnyAncestor(hasTestTag("compat-thread-post-100")), useUnmergedTree = true).assertIsDisplayed()
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

    @Test fun tabTitlesStayInsideTheirBackgroundBand() {
        prepareThread("default", true)
        runBlocking {
            store.savePreference("compat.design.designTabSelectorOpened", "ON")
            val otherUrl = "https://may.2chan.net/b/res/200.htm"
            store.openTab(CompatTab(
                key = compatTabKey(otherUrl), canonicalUrl = otherUrl, originalUrl = otherUrl,
                boardKey = compatBoardKey("https://may.2chan.net/b/"), boardName = "検証",
                threadNo = "200", title = "比較タブ", replyCount = 0,
                insertedAtEpochMillis = 2, contentUpdatedAtEpochMillis = 2
            ))
        }
        showThread()
        val selectorTitle = rule.onNode(
            hasText("スレ本文") and hasAnyAncestor(hasTestTag("compat-tab-selector")),
            useUnmergedTree = true
        ).assertIsDisplayed()
        val band = rule.onNodeWithTag("compat-tab-title-scrim-$tabKey", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val bounds = selectorTitle.fetchSemanticsNode().boundsInRoot
        val layouts = mutableListOf<TextLayoutResult>()
        selectorTitle.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        screenshot("tab-title-band")
        assertEquals(1, layouts.single().lineCount)
        val glyphPixels = selectorTitle.captureToImage().toPixelMap()
        assertTrue("Title must paint white glyphs", (0 until glyphPixels.width).any { x ->
            (0 until glyphPixels.height).any { y ->
                val pixel = glyphPixels[x, y]
                pixel.red > 0.9f && pixel.green > 0.9f && pixel.blue > 0.9f
            }
        })
        val inactiveKey = compatTabKey("https://may.2chan.net/b/res/200.htm")
        val inactiveBand = rule.onNodeWithTag("compat-tab-title-scrim-$inactiveKey", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val inactiveTitle = rule.onNode(
            hasText("比較タブ") and hasAnyAncestor(hasTestTag("compat-tab-selector")),
            useUnmergedTree = true
        ).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Inactive title must also fit its dark band", inactiveTitle.top >= inactiveBand.top - 1f && inactiveTitle.bottom <= inactiveBand.bottom + 1f)
        assertTrue("Title must start within its background: title=$bounds band=$band", bounds.top >= band.top - 1f)
        assertTrue("Title must end within its background: title=$bounds band=$band", bounds.bottom <= band.bottom + 1f)
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

    @Test fun modernFlatLightShowsDeletionSummaryBelowOp() = modernDeletionSummary(false, false)
    @Test fun modernFlatDarkShowsDeletionSummaryBelowOp() = modernDeletionSummary(false, true)
    @Test fun modernTreeLightShowsDeletionSummaryBelowOp() = modernDeletionSummary(true, false)
    @Test fun modernTreeDarkShowsDeletionSummaryBelowOp() = modernDeletionSummary(true, true)

    private fun modernDeletionSummary(tree: Boolean, dark: Boolean) {
        val posts = listOf(com.valoser.futacha.shared.model.Post(
            id = "100", author = null, subject = null, timestamp = "", messageHtml = "スレ本文",
            imageUrl = null, thumbnailUrl = null
        )) + PostDeletionKind.entries.take(4).mapIndexed { index, kind ->
            com.valoser.futacha.shared.model.Post(
                id = "${index + 101}", order = index + 1, author = null, subject = null, timestamp = "",
                messageHtml = "<font color=\"#ff0000\">${kind.notice}</font><br>元の本文${index + 1}",
                imageUrl = null, thumbnailUrl = null,
                isDeleted = kind != PostDeletionKind.ISOLATED, isIsolated = kind == PostDeletionKind.ISOLATED
            )
        }
        val page = androidx.compose.runtime.mutableStateOf(com.valoser.futacha.shared.model.ThreadPage(
            threadId = "100", boardTitle = "検証", expiresAtLabel = null,
            deletedNotice = "削除された記事が7件あります.見る", posts = posts
        ))
        val expected = "削除・隔離されたレス：7件（スレ主：1件／投稿者本人：1件／管理者：1件／隔離：1件／種類不明：3件）"
        rule.setContent {
            MaterialTheme(colorScheme = if (dark) androidx.compose.material3.darkColorScheme(background = Color.Black, surface = Color.Black) else androidx.compose.material3.lightColorScheme()) {
                androidx.compose.material3.Surface {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    if (tree) {
                        com.valoser.futacha.shared.ui.board.ThreadTreeContent(
                            page = page.value, deletionSummary = com.valoser.futacha.shared.ui.board.threadDeletionSummaryForPage(page.value),
                            embeddedHtml = emptyList(), summaryState = null, listState = listState,
                            saidaneOverrides = emptyMap(), searchHighlightRanges = emptyMap(),
                            onPostLongPress = {}, onQuoteRequestedForPost = {}, onSaidaneClick = {},
                            onUrlClick = {}, onRefresh = {}, isRefreshing = false
                        )
                    } else {
                        com.valoser.futacha.shared.ui.board.ThreadContent(
                            page = page.value, deletionSummary = com.valoser.futacha.shared.ui.board.threadDeletionSummaryForPage(page.value),
                            embeddedHtml = emptyList(), summaryState = null, listState = listState,
                            saidaneOverrides = emptyMap(), searchHighlightRanges = emptyMap(),
                            onPostLongPress = {}, onQuoteRequestedForPost = {}, onSaidaneClick = {},
                            onUrlClick = {}, onRefresh = {}, isRefreshing = false
                        )
                    }
                }
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText(expected, useUnmergedTree = true).fetchSemanticsNodes(false).isNotEmpty() }
        val summary = rule.onNodeWithText(expected, useUnmergedTree = true).assertIsDisplayed()
        assertReadableText(summary, if (dark) "black" else "default")
        assertTrue(summary.fetchSemanticsNode().boundsInRoot.top >= rule.onNodeWithText("スレ本文", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.bottom)
        screenshot("modern-summary-${if (tree) "tree" else "flat"}-${if (dark) "dark" else "light"}")
        PostDeletionKind.entries.take(4).forEach { kind ->
            rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(kind.notice))
            val collapsed = rule.onNodeWithText(kind.notice, useUnmergedTree = true).assertIsDisplayed()
            val collapsedLayouts = mutableListOf<TextLayoutResult>()
            collapsed.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(collapsedLayouts) }
            assertEquals(Color.Red, collapsedLayouts.single().layoutInput.style.color)
            rule.onAllNodesWithText("本文を表示").onFirst().performScrollTo().performClick()
            val node = rule.onNodeWithText(kind.notice, substring = true, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.single().layoutInput.text.spanStyles.any { it.start == 0 && it.end == kind.notice.length && it.item.color == Color.Red })
            assertReadableText(node, if (dark) "black" else "default")
        }
        rule.runOnIdle { page.value = page.value.copy(deletedNotice = null, posts = posts.take(1)) }
        rule.onNodeWithText(expected, useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("スレ本文", useUnmergedTree = true).assertIsDisplayed()
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
