@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.ai.*
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import com.valoser.futacha.shared.ui.*
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Parser -> actual preview/router -> production screens/storage -> local fake accepted send. Keys never enter diagnostics. */
class M64ExternalDraftKeyInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val boardUrl = "https://may.2chan.net/b/"
    private val threadUrl = "${boardUrl}res/1001.htm"
    private val board = BoardSummary("m64", "M64", "test", boardUrl, "")
    private val boardKey = compatBoardKey(boardUrl)
    private val tabKey = compatTabKey(threadUrl)
    private val ownerKey = "owner64"
    private val typedKey = "typed64"
    private val externalKey = "link64"
    private val body = "M64 local command body"
    private val sentKeys = CopyOnWriteArrayList<String>()
    private val releaseSend = CompletableDeferred<Unit>()
    private val accepted = AtomicInteger()
    private val writes = CopyOnWriteArrayList<String>()
    private val storage = object : BaseInMemoryPlatformStateStorage() {
        override suspend fun updateLastUsedDeleteKey(value: String) { writes += value; super.updateLastUsedDeleteKey(value) }
    }
    private val appStore = AppStateStore(storage)
    private val database = "m64-${System.nanoTime()}.db"
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var images: ImageLoader
    private var incoming by mutableStateOf<FutachaAiCommand?>(null)
    private val preferences = mapOf(FUTACHA_DRAFT_PERSIST_STORAGE_KEY to "ON", "compat.control.controlPostConfirm" to "OFF",
        "compat.control.controlPostDestinationConfirm" to "OFF", COMPAT_POST_DELETE_KEY_STORAGE_KEY to ownerKey)
    private val compatBoard = CompatBoard(boardKey, "M64", boardUrl, boardUrl, 0)
    private val tab = CompatTab(key = tabKey, canonicalUrl = threadUrl, originalUrl = threadUrl, boardKey = boardKey,
        boardName = "M64", threadNo = "1001", title = "M64", insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)

    @Before fun prepare() {
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        images = ImageLoader.Builder(rule.activity).build()
        runBlocking {
            store.initialize(); store.upsertBoard(compatBoard); store.openTab(tab); store.savePreferences(preferences)
            appStore.setHasShownPostingNotice(true); appStore.setAiCommandEnabled(true); appStore.setLastUsedDeleteKey(ownerKey)
        }
        writes.clear()
    }
    @After fun cleanup() {
        releaseSend.complete(Unit)
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
        images.shutdown(); appStore.close()
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(database)
    }
    private fun page() = ThreadPage("1001", "M64", null, null, listOf(
        Post("1001", 0, "fixture", null, "now", messageHtml = "M64 ready", imageUrl = null, thumbnailUrl = null)))
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThread(board: String, threadId: String) = page()
        override suspend fun getThreadByUrl(threadUrl: String) = page()
        override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(page())
        override suspend fun getThreadContentByUrl(threadUrl: String) = ThreadPageContent(page())
        override suspend fun getCatalog(board: String, mode: CatalogMode): List<CatalogItem> = emptyList()
        override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): List<CatalogItem> = emptyList()
        override suspend fun replyToThread(board: String, threadId: String, name: String, email: String, subject: String,
            comment: String, password: String, imageFile: ByteArray?, imageFileName: String?, textOnly: Boolean, handwriting: Boolean): String? {
            sentKeys += password; releaseSend.await(); accepted.incrementAndGet(); return "1002"
        }
        override suspend fun createThread(board: String, name: String, email: String, subject: String, comment: String,
            password: String, imageFile: ByteArray?, imageFileName: String?, textOnly: Boolean, handwriting: Boolean): String? {
            sentKeys += password; releaseSend.await(); accepted.incrementAndGet(); return "1002"
        }
    }

    @Test fun modernReplyUsesTheSavedDefaultThroughDraftAndAcceptedSend() = modern(build = false, typed = false)
    @Test fun modernReplyKeepsTheKeyAlreadyTypedByTheUser() = modern(build = false, typed = true)
    @Test fun modernThreadDraftUsesTheSavedDefaultThroughAcceptedCreate() = modern(build = true, typed = false)
    @Test fun modernThreadDraftKeepsTheKeyAlreadyTypedByTheUser() = modern(build = true, typed = true)

    private fun modern(build: Boolean, typed: Boolean) {
        val expected = if (typed) typedKey else ownerKey
        val fileSystem = createFileSystem(rule.activity)
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides images, LocalFutachaCatalogImageLoader provides images) {
            ProvideFutachaSharedFeatures(store, null, repository, fileSystem, null, "test", appStateStore = appStore, catalogImageLoader = images) {
                if (build) CatalogScreen(board, emptyList(), onBack = {}, onThreadSelected = {}, repository = repository,
                    stateStore = appStore, preferencesState = ScreenPreferencesState("test", isTelemetryCollectionEnabled = false),
                    aiCommand = incoming, onAiCommandConsumed = { incoming = null })
                else ThreadScreen(board, emptyList(), "1001", "M64", 1, repository = repository, stateStore = appStore,
                    preferencesState = ScreenPreferencesState("test", isTelemetryCollectionEnabled = false), onBack = {},
                    aiCommand = incoming, onAiCommandConsumed = { incoming = null })
            }
        } } }
        if (!build) rule.waitUntil(15000) { rule.onAllNodesWithText("M64 ready", substring = true).fetchSemanticsNodes().isNotEmpty() }
        if (typed) {
            if (build) {
                // CatalogNavigationBar uses this label for BOTH icon description
                // and text. Its shared-feature provider can still be loading
                // after setContent returns, so await the actual clickable item.
                val create = hasClickAction() and (hasContentDescription("スレッド作成") or hasText("スレッド作成"))
                rule.waitUntil(5_000) { rule.onAllNodes(create).fetchSemanticsNodes().isNotEmpty() }
                rule.onAllNodes(create).onFirst().performClick()
            } else rule.onAllNodesWithContentDescription("返信").onFirst().performClick()
            rule.onNode(hasSetTextAction() and hasText("削除キー")).performTextReplacement(typedKey)
        }
        val action = if (build) FutachaAiAction.DraftThread else FutachaAiAction.DraftReply
        val command = parsedCommand(action)
        val preview = preview(command)
        val previewExplains = "削除キー" in preview && "使用しません" in preview
        assertTrue("Preview must not execute a post", sentKeys.isEmpty())
        assertTrue("Preview must not alter the default key", runBlocking { appStore.lastUsedDeleteKey.first() } == ownerKey)
        rule.runOnIdle { incoming = command }
        rule.waitUntil(10000) { rule.onAllNodes(hasSetTextAction() and hasText("コメント")).fetchSemanticsNodes().any {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == body
        } }
        rule.waitUntil(10000) { runBlocking {
            if (build) store.loadBuildDraft(boardKey)?.comment == body else store.loadDraft(tabKey)?.comment == body
        } }
        // Modern draft persistence intentionally does not save the form's key;
        // it only preserves a pre-existing stored key (FutachaDraftPersistence).
        // The compatibility case below separately seeds and checks that stored key.
        val draftSafe = runBlocking { if (build) store.loadBuildDraft(boardKey)?.deleteKey != externalKey else store.loadDraft(tabKey)?.deleteKey != externalKey }
        val beforeSafe = runBlocking { appStore.lastUsedDeleteKey.first() } == ownerKey
        rule.onAllNodesWithContentDescription("送信する").onFirst().performClick()
        rule.waitUntil(10000) { sentKeys.size == 1 }
        // Storage writes are not a prerequisite of posting: a default may stay
        // unchanged or be persisted asynchronously. Inspect the real values at
        // the suspended posting boundary, then also inspect accepted-send state.
        assertTrue("The local posting API must receive the trusted saved or typed key", sentKeys.single() == expected)
        assertEquals("The local repository must still be waiting for acceptance", 0, accepted.get())
        val sendingDefault = runBlocking { appStore.lastUsedDeleteKey.first() }
        val sendingSafe = sendingDefault in setOf(ownerKey, expected) && writes.none { it == externalKey }
        releaseSend.complete(Unit)
        rule.waitUntil(10000) { accepted.get() == 1 }
        rule.waitUntil(10000) { runBlocking { if (build) store.loadBuildDraft(boardKey) == null else store.loadDraft(tabKey) == null } }
        assertTrue("The accepted posting API must retain the trusted key", sentKeys.single() == expected)
        assertTrue("An external key entered a persisted draft", draftSafe)
        assertTrue("Creating a draft changed the default key", beforeSafe)
        assertTrue("Sending wrote an external key as the default", sendingSafe)
        assertTrue("The existing preview must explain ignored external key input", previewExplains)
        assertTrue("Accepted send did not preserve the user's key", runBlocking { appStore.lastUsedDeleteKey.first() } == expected)
        assertTrue("The default key history contains external input", writes.none { it == externalKey })
    }

    @Test fun compatibilityReplyMergeAndAcceptedSendKeepTheStoredDraftKey() {
        runBlocking { store.saveDraft(CompatReplyDraft(tabKey, comment = "old body", deleteKey = typedKey, updatedAtEpochMillis = 1)) }
        val command = parsedCommand(FutachaAiAction.DraftReply)
        val preview = compatPlatformAiConfirmationMessage(command)
        assertTrue("Preview must not reveal a key", externalKey !in preview)
        runBlocking { persistCompatPlatformAiReplyDraft(store, tabKey, command) }
        val draftSafe = runBlocking { store.loadDraft(tabKey) }?.deleteKey == typedKey
        rule.setContent { CompatibilityProfileTheme("mono") { CompositionLocalProvider(LocalFutachaImageLoader provides images, LocalFutachaCatalogImageLoader provides images) {
            CompatPostScreen(tab, compatBoard, repository, store = store, preferences = preferences, appVersion = "test",
                fileSystem = null, onToolbarEdit = {}, onPostSent = {}, onBack = {})
        } } }
        rule.waitUntil(10000) { rule.onAllNodes(hasSetTextAction() and hasText("コメント")).fetchSemanticsNodes().any {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == body
        } }
        rule.onAllNodesWithContentDescription("送信する").onFirst().performClick()
        rule.waitUntil(10000) { sentKeys.size == 1 }
        assertTrue("An unaccepted send changed the stored default", runBlocking { store.preferences.first() }[COMPAT_POST_DELETE_KEY_STORAGE_KEY] == ownerKey)
        releaseSend.complete(Unit)
        rule.waitUntil(10000) { accepted.get() == 1 && runBlocking { store.loadDraft(tabKey) } == null }
        assertTrue("The external key reached the local posting API", sentKeys.single() == typedKey)
        assertTrue("The external command replaced a stored draft key", draftSafe)
        assertTrue("The preview must explain ignored external input", "削除キー" in preview && "使用しません" in preview)
        assertTrue("Accepted send overwrote the default with an external key", runBlocking { store.preferences.first() }[COMPAT_POST_DELETE_KEY_STORAGE_KEY] == typedKey)
    }

    private fun parsedCommand(action: FutachaAiAction): FutachaAiCommand = checkNotNull(parseFutachaAiDeepLink(
        "futacha://ai?action=${action.id}&boardId=m64&threadId=1001&comment=M64%20local%20command%20body&PaSsWoRd=$externalKey&delete_key=$externalKey&source=android-app-functions"))
    private fun preview(command: FutachaAiCommand): String = runBlocking {
        var navigation = FutachaNavigationState(selectedBoardId = board.id, selectedThreadId = if (command.action == FutachaAiAction.DraftReply) "1001" else null)
        val inputs = FutachaAiRouterInputs(appStore, listOf(board), emptyList(), navigation, { navigation = it },
            null, null, null, false, "test", true)
        val outcome = executeFutachaAiCommand(command, inputs)
        check(outcome is FutachaAiCommandOutcome.NeedsConfirmation) { "Expected the production confirmation boundary" }
        assertTrue("Preview must not reveal a key", externalKey !in outcome.request.message)
        val confirmed = executeFutachaAiCommand(outcome.request.command, inputs, confirmed = true)
        check(confirmed is FutachaAiCommandOutcome.NeedsForeground) { "Expected delivery to the target screen" }
        outcome.request.message
    }
}
