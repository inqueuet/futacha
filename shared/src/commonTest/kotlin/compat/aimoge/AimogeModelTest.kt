package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.diffCompatCatalogGenerations
import com.valoser.futacha.shared.compat.toCompatPostSnapshot
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.PostAttachment
import com.valoser.futacha.shared.model.SavedAttachment
import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.numericId
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val UUID = "0193b2c4-1a2b-7c3d-8e4f-5a6b7c8d9e0f"

class AimogeModelTest {
    private val storeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val oldReader = Json { ignoreUnknownKeys = true }

    private fun item(id: String, url: String, siteOrder: Long? = null) =
        CatalogItem(id, url, "t", null, null, replyCount = 0, siteOrder = siteOrder)

    private val files = listOf(
        PostAttachment("https://cdn.nijiurachan.net/a.jpg", "https://cdn.nijiurachan.net/a_s.jpg", 800, 600),
        PostAttachment("https://cdn.nijiurachan.net/b.png"),
        PostAttachment("https://cdn.nijiurachan.net/c.webm", mimeType = "video/webm")
    )

    @Test
    fun numericIdUsesTheOpeningPostNumberForAimoge() {
        assertEquals(1439323740L, item("1439323740", "https://may.2chan.net/b/res/1439323740.htm").numericId())
        assertEquals(42L, item(UUID, "https://nijiurachan.net/b/aimoge/thread/$UUID", 42L).numericId())
        // A UUID without the number sorts as 0, never as the digits of the UUID.
        assertEquals(0L, item(UUID, "https://nijiurachan.net/b/aimoge/thread/$UUID").numericId())
    }

    @Test
    fun anAimogeCatalogMakesNoVanishedThreads() {
        val current = listOf(item(UUID, "https://nijiurachan.net/b/aimoge/thread/$UUID", 2L))
        val previous = listOf(item("0193b2c4-1a2b-7c3d-8e4f-000000000000", "https://nijiurachan.net/b/aimoge/thread/0193b2c4-1a2b-7c3d-8e4f-000000000000", 1L))
        val diff = diffCompatCatalogGenerations(current, previous, requestedThreadCount = 300, enabled = true)
        assertTrue(diff.vanishedWithin.isEmpty())
        assertTrue(diff.vanishedBottom.isEmpty())
    }

    @Test
    fun theSnapshotCarriesEveryFileAndAFutabaPostIsUnchanged() {
        val futaba = Post(id = "1", author = null, subject = null, timestamp = "t", messageHtml = "m", imageUrl = "https://may.2chan.net/b/src/1.jpg", thumbnailUrl = null)
        val futabaSnapshot = futaba.toCompatPostSnapshot(0)
        assertTrue(futabaSnapshot.attachments.isEmpty())
        assertFalse(storeJson.encodeToString(CompatPostSnapshot.serializer(), futabaSnapshot).contains("attachments"))

        val aimoge = futaba.copy(imageUrl = files[0].url, attachments = files, siteUuid = UUID, displayNumber = 3)
        val snapshot = aimoge.toCompatPostSnapshot(2)
        assertEquals(files, snapshot.attachments)
        val encoded = storeJson.encodeToString(CompatPostSnapshot.serializer(), snapshot)
        assertEquals(snapshot, storeJson.decodeFromString(CompatPostSnapshot.serializer(), encoded))
        // An old reader that has no such field ignores it.
        assertTrue(encoded.contains("\"attachments\""))
        oldReader.decodeFromString(CompatPostSnapshot.serializer(), encoded.replace("\"attachments\"", "\"unknownField\""))
    }

    @Test
    fun aSavedPostKeepsTheExtraFilesAndOldMetadataStillReads() {
        val saved = SavedPost("1", 0, null, null, "t", "m", null, null, null, null, null, null)
        val plain = oldReader.encodeToString(SavedPost.serializer(), saved)
        assertFalse(plain.contains("extraAttachments"))
        val withExtras = saved.copy(extraAttachments = listOf(SavedAttachment("https://cdn.nijiurachan.net/b.png", "images/b.png")))
        val encoded = oldReader.encodeToString(SavedPost.serializer(), withExtras)
        assertEquals(withExtras, oldReader.decodeFromString(SavedPost.serializer(), encoded))
        assertEquals(saved, oldReader.decodeFromString(SavedPost.serializer(), plain))
    }
}
