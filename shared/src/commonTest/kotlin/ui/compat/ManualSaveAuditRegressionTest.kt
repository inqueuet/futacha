package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.ui.board.requiresVisibleManualSaveDestination
import com.valoser.futacha.shared.ui.board.savedThreadCompletionSummary
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class ManualSaveAuditRegressionTest {
    private fun metadata(storageId: String = "b__123", threadId: String = "123", savedAt: Long = 1000) = SavedThreadMetadata(
        threadId, "b", "may/b", "https://may.2chan.net/b/", "saved thread", storageId, savedAt,
        null, emptyList(), 50, rawHtmlPath = "$threadId.htm"
    )
    private fun saved() = SavedThread("123", "b", "may/b", "saved thread", "b__123", null,
        1000, 0, 0, 0, 50, SaveStatus.COMPLETED)

    @Test
    fun completedCompatSaveAppearsOnlyInTheSelectedFolderIndex() = runBlocking {
        listOf<SaveLocation?>(null, SaveLocation.Path("chosen"), SaveLocation.TreeUri("content://tree/pictures"),
            SaveLocation.Bookmark("selected-bookmark")).forEach { location ->
            val fs = InMemoryFileSystem()
            val message = completeCompatThreadSave(saved(), fs, location)
            val repository = createCompatSavedThreadRepository(fs, location)
            assertEquals(listOf(saved()), repository.loadIndex().threads)
            assertTrue(message.contains("保存先:"))
            if (location != null) assertTrue(createCompatSavedThreadRepository(fs, null).loadIndex().threads.isEmpty())
        }
    }

    @Test
    fun recoversPreviouslyUnindexedMetadataAndDoesNotDuplicateIt() = runBlocking {
        listOf<SaveLocation?>(null, SaveLocation.Path("chosen"), SaveLocation.TreeUri("content://tree/pictures"),
            SaveLocation.Bookmark("selected-bookmark")).forEach { location ->
            val fs = InMemoryFileSystem()
            val base = location ?: SaveLocation.Path("saved_threads")
            fs.writeString(base, "b__123/metadata.json", Json.encodeToString(metadata())).getOrThrow()
            val repository = createCompatSavedThreadRepository(fs, location)
            assertEquals(1, repository.recoverUnindexedThreads().getOrThrow())
            assertEquals("123", repository.loadIndex().threads.single().threadId)
            assertEquals(0, repository.recoverUnindexedThreads().getOrThrow())
            repository.deleteThread("123", "b").getOrThrow()
            assertEquals(0, repository.recoverUnindexedThreads().getOrThrow())
            assertTrue(repository.loadIndex().threads.isEmpty())
        }
    }

    @Test
    fun recoverySkipsMalformedMetadataAndChoosesNewestDuplicate() = runBlocking {
        val fs = InMemoryFileSystem()
        fs.writeString("saved_threads/bad/metadata.json", "not json").getOrThrow()
        fs.writeString("saved_threads/old/metadata.json", Json.encodeToString(metadata("old", savedAt = 1))).getOrThrow()
        fs.writeString("saved_threads/new/metadata.json", Json.encodeToString(metadata("new", savedAt = 2))).getOrThrow()
        val repository = createCompatSavedThreadRepository(fs, null)
        assertEquals(1, repository.recoverUnindexedThreads().getOrThrow())
        assertEquals("new", repository.loadIndex().threads.single().storageId)
        assertTrue(fs.exists("saved_threads/old/metadata.json"))
    }

    @Test
    fun recoveryMarksLegacyMissingHtmlAsPartialWhileKeepingTheBody() = runBlocking {
        val fs = InMemoryFileSystem()
        fs.writeString("saved_threads/b__123/metadata.json", Json.encodeToString(metadata().copy(rawHtmlPath = null))).getOrThrow()
        val repository = createCompatSavedThreadRepository(fs, null)
        repository.recoverUnindexedThreads().getOrThrow()
        val recovered = repository.loadIndex().threads.single()
        assertEquals(SaveStatus.PARTIAL, recovered.status)
        assertTrue(recovered.isHtmlMissing)
        assertTrue(savedThreadCompletionSummary(recovered).contains("HTMLを保存できませんでした"))
    }

    @Test
    fun partialCompletionDistinguishesMissingHtmlAndFailedMedia() {
        val message = savedThreadCompletionSummary(saved().copy(status = SaveStatus.PARTIAL, isHtmlMissing = true, incompleteMediaCount = 3))
        assertTrue(message.contains("HTMLを保存できませんでした"))
        assertTrue(message.contains("3"))
        assertFalse(message.startsWith("スレッドを保存しました"))
    }

    @Test
    fun initialAndroidSaveRequiresVisibleFolderAndIosDocumentsRemainsUsable() {
        assertTrue(requiresVisibleManualSaveDestination(true, null))
        assertTrue(requiresVisibleManualSaveDestination(true, SaveLocation.Path("Documents")))
        assertFalse(requiresVisibleManualSaveDestination(true, SaveLocation.TreeUri("content://tree/folder")))
        assertFalse(requiresVisibleManualSaveDestination(false, null))
        assertFalse(requiresVisibleManualSaveDestination(false, SaveLocation.Bookmark("bookmark")))
    }
}
