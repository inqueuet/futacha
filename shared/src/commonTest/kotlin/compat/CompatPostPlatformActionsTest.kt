package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.ui.board.ATTACHMENT_LOAD_FAILURE_MESSAGE
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.TextEncoding
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatPostPlatformActionsTest {
    @Test
    fun speechResultUsesTargetPunctuationRules() {
        assertEquals("一行目\n二行目三行目\n四行目", normalizeCompatSpeechResult("一行目。二行目、三行目改行四行目"))
        assertEquals("先頭\n末尾", appendCompatPostText("先頭", "末尾"))
    }

    @Test
    fun postCounterUsesCrLfAndShiftJis() {
        assertEquals(1, compatPostLineCount(""))
        assertEquals(0, compatPostLineCount("", emptyIsOneLine = false))
        assertEquals(2, compatPostLineCount("あ\nい"))
        assertEquals(6, compatPostShiftJisByteCount("あ\nい"))
        assertTrue(compatPostShiftJisByteCount("日本語") > "日本語".length)
    }

    @Test
    fun postCounterCountsEmojiAsTheNumericReferenceSentOverShiftJis() {
        val expectedWireBytes = TextEncoding.encodeToShiftJis("A&#128512;B").size
        assertEquals(expectedWireBytes, compatPostShiftJisByteCount("A😀B"))
    }

    @Test
    fun deviceInfoUsesTheReferenceCompatibilityIdentityAndSeparators() {
        assertEquals(
            "ふたば＠アプリ としあき(仮) 8.5 google/Pixel 9/16",
            formatCompatPostDeviceInfo("8.5", "google", "Pixel 9", "16")
        )
        assertEquals(
            "ふたば＠アプリ としあき(仮) 8.5 Apple/iPhone/iOS 18.6",
            formatCompatPostDeviceInfo("8.5", "Apple", "iPhone", "iOS 18.6")
        )
        assertEquals("添付画像", compatPostAttachmentToolbarLabel(hasAttachment = false))
        assertEquals("添付削除", compatPostAttachmentToolbarLabel(hasAttachment = true))
    }

    @Test
    fun upsUploadStartsWithEmptyCommentAndTheRememberedDeleteKey() {
        assertEquals(
            CompatUpsUploadInitialFields(comment = "", deleteKey = "248600"),
            compatUpsUploadInitialFields("248600")
        )
    }

    @Test
    fun attachmentDecisionSeparatesEveryTargetErrorAndCompressionBoundary() {
        assertEquals("添付ファイルが読み込めません", ATTACHMENT_LOAD_FAILURE_MESSAGE)
        val limit = 3_000_000
        assertEquals(
            CompatPostAttachmentDecision.EmptyPayload,
            decideCompatPostAttachment(ImageData(byteArrayOf(), "empty.png"), limit)
        )
        assertEquals(
            CompatPostAttachmentDecision.MissingFileName,
            decideCompatPostAttachment(ImageData(byteArrayOf(1), ""), limit)
        )
        assertEquals(
            CompatPostAttachmentDecision.UnsupportedExtension,
            decideCompatPostAttachment(ImageData(byteArrayOf(1), "payload.zip"), limit)
        )
        assertEquals(
            CompatPostAttachmentDecision.OversizedVideo,
            decideCompatPostAttachment(ImageData(ByteArray(limit + 1), "movie.webm"), limit)
        )
        assertEquals(
            CompatPostAttachmentDecision.AskImageCompression,
            decideCompatPostAttachment(ImageData(ByteArray(limit + 1), "photo.png"), limit)
        )
        assertEquals(
            CompatPostAttachmentDecision.Accept,
            decideCompatPostAttachment(ImageData(ByteArray(limit), "photo.jpeg"), limit)
        )
        assertEquals(8_192_000, compatPostAttachmentLimitBytes("https://may.2chan.net/b/"))
        assertEquals(3_072_000, compatPostAttachmentLimitBytes("https://img.2chan.net/b/"))
        assertEquals(3_072_000, compatPostAttachmentLimitBytes("https://dat.2chan.net/b/"))
        assertEquals(
            CompatPostAttachmentDecision.Accept,
            decideCompatPostAttachment(
                ImageData(byteArrayOf(1), "modern.webp"),
                8_192_000,
                setOf("jpg", "png", "webp", "webm", "mp4")
            )
        )
        assertEquals(
            CompatPostAttachmentDecision.Accept,
            decideCompatPostAttachment(
                ImageData(byteArrayOf(1), "modern.webm"),
                8_192_000,
                setOf("jpg", "png", "webp", "webm", "mp4")
            )
        )
        assertEquals(
            CompatPostAttachmentDecision.UnsupportedExtension,
            decideCompatPostAttachment(
                ImageData(byteArrayOf(1), "movie.webm"),
                3_000_000,
                setOf("gif", "jpg", "jpeg", "png")
            )
        )
        assertEquals(
            "ファイルサイズが0です",
            compatPostAttachmentDecisionMessage(CompatPostAttachmentDecision.EmptyPayload, "empty.png", limit)
        )
        assertEquals(
            "ファイル名が不明です",
            compatPostAttachmentDecisionMessage(CompatPostAttachmentDecision.MissingFileName, "", limit)
        )
        assertEquals(
            "対応しないフォーマットです\nzip",
            compatPostAttachmentDecisionMessage(CompatPostAttachmentDecision.UnsupportedExtension, "payload.zip", limit)
        )
        assertEquals(
            "ファイルサイズ超過です\n8MBまで",
            compatPostAttachmentDecisionMessage(CompatPostAttachmentDecision.AskImageCompression, "photo.png", 8_192_000)
        )
        assertEquals(
            "ファイルサイズ超過です\n3MBまで",
            compatPostAttachmentDecisionMessage(CompatPostAttachmentDecision.OversizedVideo, "movie.webm", 3_072_000)
        )
    }

    @Test
    fun attachmentLocatorCannotEscapePrivateDraftDirectory() {
        val locator = compatPostAttachmentLocator("https://may.2chan.net/b/res/1.htm", "../../危険 な画像.jpg")

        assertTrue(isCompatPostAttachmentLocator(locator))
        assertTrue(locator.startsWith("private/compat_post_attachments/"))
        assertFalse(".." in locator)
        assertTrue(locator.endsWith(".jpg"))
    }

    @Test
    fun attachmentPayloadSurvivesReloadAndContainerCleanup() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val payload = byteArrayOf(1, 2, 3, 4)
        val locator = persistCompatPostAttachment(
            fileSystem = fileSystem,
            tabKey = "tab-key",
            attachment = ImageData(bytes = payload, fileName = "sample.png")
        ).getOrThrow()

        val restored = loadCompatPostAttachment(fileSystem, locator).getOrThrow()
        assertEquals("sample.png", restored.fileName)
        assertTrue(restored.bytes.contentEquals(payload))

        deleteCompatPostAttachment(fileSystem, locator, deleteContainer = true).getOrThrow()
        assertFalse(fileSystem.exists(locator))
    }

    @Test
    fun drawingCopyUsesConfiguredSaveLocationAndReferenceFileName() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val payload = byteArrayOf(9, 8, 7)
        val fileName = persistCompatDrawingCopy(
            fileSystem = fileSystem,
            location = SaveLocation.Path("/pictures"),
            drawing = ImageData(payload, "ignored.png"),
            timestampEpochMillis = 1234L
        )?.getOrThrow()

        assertTrue(fileName?.matches(Regex("drawing_\\d{8}_\\d{6}\\.png")) == true)
        assertTrue(fileSystem.readBytes("/pictures/$fileName").getOrThrow().contentEquals(payload))
        assertEquals(
            null,
            persistCompatDrawingCopy(
                fileSystem,
                null,
                ImageData(payload, "ignored.png"),
                1234L
            )
        )
    }

    @Test
    fun sameFileNameWithDifferentPayloadUsesDifferentLocator() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val first = persistCompatPostAttachment(
            fileSystem,
            "tab-key",
            ImageData(byteArrayOf(1, 2, 3), "same.png")
        ).getOrThrow()
        val second = persistCompatPostAttachment(
            fileSystem,
            "tab-key",
            ImageData(byteArrayOf(3, 2, 1), "same.png")
        ).getOrThrow()

        assertTrue(first != second)
        assertTrue(fileSystem.exists(first))
        assertTrue(fileSystem.exists(second))
    }

    @Test
    fun cleanupRemovesDiscardedPayloadButKeepsRetainedPayloadInSameContainer() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val discarded = persistCompatPostAttachment(
            fileSystem,
            "tab-key",
            ImageData(byteArrayOf(1), "discarded.png")
        ).getOrThrow()
        val retained = persistCompatPostAttachment(
            fileSystem,
            "tab-key",
            ImageData(byteArrayOf(2), "retained.png")
        ).getOrThrow()

        val removed = cleanupCompatPostAttachmentLocators(
            fileSystem = fileSystem,
            candidateLocators = setOf(discarded, "../../invalid"),
            retainedLocators = setOf(retained)
        ).getOrThrow()

        assertEquals(1, removed)
        assertFalse(fileSystem.exists(discarded))
        assertTrue(fileSystem.exists(retained))
    }
}
