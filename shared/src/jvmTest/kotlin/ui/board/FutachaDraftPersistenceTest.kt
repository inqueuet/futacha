package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatReplyDraft
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.ui.compat.FUTACHA_DRAFT_PERSIST_PREFERENCE_KEY
import com.valoser.futacha.shared.ui.compat.FUTACHA_DRAFT_PERSIST_STORAGE_KEY
import com.valoser.futacha.shared.ui.compat.compatBooleanPreferenceSummary
import com.valoser.futacha.shared.ui.compat.compatIsBooleanPreference
import com.valoser.futacha.shared.ui.compat.compatSettingsGroups
import com.valoser.futacha.shared.ui.compat.futachaDraftPersistEnabled
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.JvmFileSystem
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ふたちゃの「下書きを端末に保存」（機能そろえ バッチ4）：空の判定、タブ・板が共有ストアにある時だけ書くこと、
 * 送信後（空）に記録と添付ファイルが消えること、読み込みに失敗した画面では消さないこと、設定の行がふたちゃの
 * 表示にだけ出ること。
 */
class FutachaDraftPersistenceTest {
    private val boardUrl = "https://may.2chan.net/b/"
    private val threadUrl = "${boardUrl}res/1001.htm"
    private val tabKey = compatTabKey(threadUrl)
    private val boardKey = compatBoardKey(boardUrl)

    private fun <T> withStore(block: suspend (DesktopCompatibilityStore, JvmFileSystem, File) -> T): T = runBlocking {
        val directory = Files.createTempDirectory("futacha-draft").toFile()
        val fileSystem = JvmFileSystem(directory)
        val store = DesktopCompatibilityStore(fileSystem)
        try {
            store.initialize()
            block(store, fileSystem, directory)
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    private suspend fun openTab(store: DesktopCompatibilityStore) {
        store.upsertBoard(CompatBoard(boardKey, "may/b", boardUrl, boardUrl, 0))
        store.openTab(CompatTab(tabKey, threadUrl, threadUrl, boardKey, "may/b", "1001", "スレ",
            insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L))
    }

    private fun attachmentFiles(directory: File): List<File> =
        File(directory, "private/compat_post_attachments").walkTopDown().filter { it.isFile }.toList()

    @Test
    fun aNameAMailOrAnAutofilledDeleteKeyAloneIsNotADraft() {
        assertFalse(FutachaDraftFields().hasDraftContent())
        assertFalse(FutachaDraftFields(name = "としあき", email = "sage").hasDraftContent())
        assertFalse(FutachaDraftFields(comment = "  \n").hasDraftContent())
        assertTrue(FutachaDraftFields(comment = "本文").hasDraftContent())
        assertTrue(FutachaDraftFields(subject = "題名").hasDraftContent())
        assertTrue(FutachaDraftFields(image = ImageData(byteArrayOf(1), "a.png")).hasDraftContent())
        // What is left after a successful reply (subject, comment, password and picture cleared) is empty.
        val sent = clearThreadReplyDraftAfterSubmit(ThreadReplyDraft("としあき", "sage", "題", "本文", "key", ImageData(byteArrayOf(1), "a.png")))
        assertFalse(FutachaDraftFields(sent.name, sent.email, sent.subject, sent.comment, sent.imageData).hasDraftContent())
    }

    @Test
    fun nothingIsWrittenWhileTheTabOrTheBoardIsNotInTheStore() = withStore { store, fs, directory ->
        val session = FutachaDraftSession()
        val fields = FutachaDraftFields(comment = "本文", image = ImageData(byteArrayOf(1, 2, 3), "a.png"))
        assertEquals(FutachaDraftWrite.NoOwner, persistFutachaDraft(store, fs, FutachaDraftOwner.Reply(tabKey), fields, session))
        assertEquals(FutachaDraftWrite.NoOwner, persistFutachaDraft(store, fs, FutachaDraftOwner.Build(boardKey), fields, session))
        assertNull(store.loadDraft(tabKey))
        assertNull(store.loadBuildDraft(boardKey))
        assertTrue(attachmentFiles(directory).isEmpty())
    }

    @Test
    fun aReplyDraftAndItsPictureAreKeptRestoredAndGoneAfterTheReplyIsSent() = withStore { store, fs, directory ->
        openTab(store)
        val owner = FutachaDraftOwner.Reply(tabKey)
        val picture = ImageData(byteArrayOf(9, 8, 7), "photo.png", isHandwriting = true)
        val session = FutachaDraftSession()
        val fields = FutachaDraftFields("としあき", "sage", "題", "本文", picture)
        assertEquals(FutachaDraftWrite.Saved, persistFutachaDraft(store, fs, owner, fields, session, nowEpochMillis = 5L))
        val stored = assertNotNull(store.loadDraft(tabKey))
        assertEquals("本文", stored.comment)
        assertEquals("", stored.deleteKey)
        assertTrue(stored.attachmentIsHandwriting)
        assertEquals(1, attachmentFiles(directory).size)
        // The same picture is not written again.
        persistFutachaDraft(store, fs, owner, fields.copy(comment = "本文2"), session)
        assertEquals(1, attachmentFiles(directory).size)

        // Another screen (a fresh session) restores the text and the picture.
        val reopened = FutachaDraftSession()
        val restored = assertNotNull(loadFutachaDraft(store, fs, owner, reopened))
        assertEquals("本文2", restored.fields.comment)
        assertEquals(listOf<Byte>(9, 8, 7), restored.fields.image?.bytes?.toList())
        assertEquals(true, restored.fields.image?.isHandwriting)
        assertFalse(restored.attachmentLost)

        // A replaced picture leaves one file; after the reply only the name and the mail remain: record and file go.
        persistFutachaDraft(store, fs, owner, restored.fields.copy(image = ImageData(byteArrayOf(1), "b.png")), reopened)
        assertEquals(1, attachmentFiles(directory).size)
        val sent = clearThreadReplyDraftAfterSubmit(ThreadReplyDraft("としあき", "sage", "題", "本文2", "", null))
        assertEquals(FutachaDraftWrite.Deleted, persistFutachaDraft(store, fs, owner,
            FutachaDraftFields(sent.name, sent.email, sent.subject, sent.comment, sent.imageData), reopened))
        assertNull(store.loadDraft(tabKey))
        assertTrue(attachmentFiles(directory).isEmpty())
    }

    @Test
    fun aDraftOfToshiakiKeepsItsDeleteKeyAndAFailedReadDeletesNothing() = withStore { store, fs, _ ->
        openTab(store)
        store.saveDraft(CompatReplyDraft(tabKey, comment = "としあき側", deleteKey = "own", updatedAtEpochMillis = 1L))
        val owner = FutachaDraftOwner.Reply(tabKey)
        val session = FutachaDraftSession()
        assertEquals("としあき側", loadFutachaDraft(store, fs, owner, session)?.fields?.comment)
        persistFutachaDraft(store, fs, owner, FutachaDraftFields(comment = "ふたちゃ側"), session)
        assertEquals("own", store.loadDraft(tabKey)?.deleteKey)

        val unreadable = FutachaDraftSession().apply { allowDelete = false }
        assertEquals(FutachaDraftWrite.Skipped, persistFutachaDraft(store, fs, owner, FutachaDraftFields(), unreadable))
        assertEquals("ふたちゃ側", store.loadDraft(tabKey)?.comment)
    }

    @Test
    fun aNewThreadDraftIsKeptPerBoard() = withStore { store, fs, _ ->
        store.upsertBoard(CompatBoard(boardKey, "may/b", boardUrl, boardUrl, 0))
        val owner = FutachaDraftOwner.Build(boardKey)
        val session = FutachaDraftSession()
        persistFutachaDraft(store, fs, owner, FutachaDraftFields(subject = "スレ題", comment = "スレ本文"), session)
        assertEquals("スレ題", store.loadBuildDraft(boardKey)?.subject)
        val restored = assertNotNull(loadFutachaDraft(store, fs, owner, FutachaDraftSession()))
        assertEquals("スレ本文", restored.fields.comment)
        persistFutachaDraft(store, fs, owner, FutachaDraftFields(name = "としあき"), session)
        assertNull(store.loadBuildDraft(boardKey))
    }

    @Test
    fun theSwitchIsOffByDefaultAndOnlyInFutachasControlPage() {
        assertFalse(futachaDraftPersistEnabled(emptyMap()))
        assertTrue(futachaDraftPersistEnabled(mapOf(FUTACHA_DRAFT_PERSIST_STORAGE_KEY to "ON")))
        assertEquals("compat.control.controlFutachaDraftPersist", FUTACHA_DRAFT_PERSIST_STORAGE_KEY)
        fun keys(modern: Boolean, appState: Boolean) = compatSettingsGroups("control", modern, appState)
            .single { it.first == "ふたちゃ拡張" }.second.map { it.preferenceKey }
        val futacha = keys(modern = true, appState = false)
        assertEquals(FUTACHA_DRAFT_PERSIST_PREFERENCE_KEY, futacha.last())
        assertEquals(listOf("controlCloseToastDuration", "controlPostDestinationConfirm", FUTACHA_DRAFT_PERSIST_PREFERENCE_KEY), futacha)
        assertFalse(FUTACHA_DRAFT_PERSIST_PREFERENCE_KEY in keys(modern = false, appState = true))
        assertFalse(FUTACHA_DRAFT_PERSIST_PREFERENCE_KEY in keys(modern = false, appState = false))
        val entry = compatSettingsGroups("control", true, false).flatMap { it.second }.single { it.preferenceKey == FUTACHA_DRAFT_PERSIST_PREFERENCE_KEY }
        assertEquals("OFF", entry.summary)
        assertTrue(compatIsBooleanPreference("control", entry))
        assertTrue(compatBooleanPreferenceSummary(FUTACHA_DRAFT_PERSIST_PREFERENCE_KEY).contains("としあき(仮)"))
    }

    /** 監査L31：読み込み待ちの間に打った本文と、保存されていた下書きの両方を残す。 */
    @Test
    fun typedTextIsAddedAfterTheStoredDraft() {
        val stored = FutachaDraftFields("としあき", "", "題", "保存の本文", null)
        val typed = FutachaDraftFields("", "sage", "", "打った本文", null)
        val merged = mergeFutachaDraftFields(stored, typed)
        assertEquals("保存の本文\n打った本文", merged.comment)
        assertEquals("題", merged.subject)
        assertEquals("としあき", merged.name)
        assertEquals("sage", merged.email)
    }

    /** 再監査L31：プロセスの終了から戻った画面は、保存された本文に続けて打った本文を持つ。二重にしない。 */
    @Test
    fun aRestoredFormThatAlreadyHoldsTheStoredTextIsNotDoubled() {
        val stored = FutachaDraftFields("", "", "", "abc", null)
        val typed = FutachaDraftFields("", "", "", "abcd", null)
        assertEquals("abcd", mergeFutachaDraftFields(stored, typed).comment)
        assertEquals("abc", mergeFutachaDraftFields(stored, stored).comment)
    }

    /** 監査L31：読み込みに失敗した画面が保存しても、としあき(仮)が書いた削除キーを空で上書きしない。 */
    @Test
    fun aScreenWhoseReadFailedKeepsTheStoredDeleteKey() = withStore { store, fs, _ ->
        openTab(store)
        store.saveDraft(CompatReplyDraft(tabKey = tabKey, name = "", email = "", subject = "", comment = "としあきの本文",
            attachmentUri = null, attachmentIsHandwriting = false, deleteKey = "key123", updatedAtEpochMillis = 1L))
        val session = FutachaDraftSession().apply { allowDelete = false }
        persistFutachaDraft(store, fs, FutachaDraftOwner.Reply(tabKey), FutachaDraftFields(comment = "ふたちゃの本文"), session)
        assertEquals("key123", store.loadDraft(tabKey)?.deleteKey)
        assertEquals("ふたちゃの本文", store.loadDraft(tabKey)?.comment)
    }
}
