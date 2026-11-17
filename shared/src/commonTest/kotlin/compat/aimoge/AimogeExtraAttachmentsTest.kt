package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.toCompatPostSnapshot
import com.valoser.futacha.shared.model.FileType
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.PostAttachment
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.model.toThreadPage
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.service.ThreadSaveLocalFileInfo
import com.valoser.futacha.shared.service.ThreadSaveMediaRequestType
import com.valoser.futacha.shared.service.buildThreadSaveMediaDownloadKey
import com.valoser.futacha.shared.service.buildThreadSaveMediaDownloadPlan
import com.valoser.futacha.shared.service.buildThreadSaveSavedPost
import com.valoser.futacha.shared.service.resolveThreadSavePostMedia
import com.valoser.futacha.shared.ui.board.buildThreadAttachmentGalleryItems
import com.valoser.futacha.shared.ui.compat.compatViewerMediaPosts
import kotlin.test.Test
import kotlin.test.assertEquals

class AimogeExtraAttachmentsTest {
    private val base = "https://storage.nijiurachan.net/aimg"
    private fun att(name: String) = PostAttachment(url = "$base/attachments/$name.png", thumbnailUrl = "$base/thumbnails/$name.webp")
    private val three = listOf(att("a"), att("b"), att("c"))
    private val aimogePost = Post(
        id = "1", author = null, subject = null, timestamp = "", messageHtml = "x",
        imageUrl = three[0].url, thumbnailUrl = three[0].thumbnailUrl, attachments = three
    )
    private val futabaPost = Post(
        id = "2", author = null, subject = null, timestamp = "", messageHtml = "y",
        imageUrl = "https://may.2chan.net/b/src/2.jpg", thumbnailUrl = "https://may.2chan.net/b/thumb/2s.jpg"
    )

    @Test fun theViewerAndGalleryListAllThreeFilesInOrderAndAFutabaPostStaysOne() {
        val media = compatViewerMediaPosts(listOf(aimogePost.toCompatPostSnapshot(0), futabaPost.toCompatPostSnapshot(1)))
        assertEquals(three.map { it.url } + futabaPost.imageUrl, media.map { it.imageUrl })
        assertEquals(listOf(null, "1::att::1", "1::att::2", null), media.map { it.mediaKey })
        assertEquals(three.map { it.thumbnailUrl } + futabaPost.thumbnailUrl, media.map { it.thumbnailUrl })
    }

    @Test fun theFutachaGalleryHasOneItemPerFile() {
        val items = buildThreadAttachmentGalleryItems(listOf(aimogePost, futabaPost))
        assertEquals(three.map { it.url } + futabaPost.imageUrl, items.map { it.targetUrl })
        assertEquals(listOf("1", "1", "1", "2"), items.map { it.post.id })
    }

    @Test fun theSavePlanDownloadsEveryFileAndTheSavedPostKeepsTheExtras() {
        val plan = buildThreadSaveMediaDownloadPlan(listOf(aimogePost, futabaPost), maxMediaItems = 100)
        assertEquals(8, plan.totalMediaCount)
        val files = plan.scheduledItems.associate { item ->
            buildThreadSaveMediaDownloadKey(item.url, item.requestType) to ThreadSaveLocalFileInfo(
                relativePath = "b/" + item.url.substringAfterLast('/'),
                fileType = if (item.requestType == ThreadSaveMediaRequestType.THUMBNAIL) FileType.THUMBNAIL else FileType.FULL_IMAGE,
                byteSize = 1
            )
        }
        val saved = buildThreadSaveSavedPost(aimogePost, resolveThreadSavePostMedia(aimogePost, files), emptyMap(), files)
        assertEquals(listOf("b/b.png", "b/c.png"), saved.extraAttachments.map { it.localPath })
        assertEquals(listOf("b/b.webp", "b/c.webp"), saved.extraAttachments.map { it.localThumbnailPath })
        val futaba = buildThreadSaveSavedPost(futabaPost, resolveThreadSavePostMedia(futabaPost, files), emptyMap(), files)
        assertEquals(emptyList(), futaba.extraAttachments)
    }

    @Test fun aSavedCopyOpensWithAllThreeFilesAndAFutabaCopyWithNone() {
        val files = buildThreadSaveMediaDownloadPlan(listOf(aimogePost), 100).scheduledItems.associate { item ->
            buildThreadSaveMediaDownloadKey(item.url, item.requestType) to ThreadSaveLocalFileInfo(
                "b/" + item.url.substringAfterLast('/'),
                if (item.requestType == ThreadSaveMediaRequestType.THUMBNAIL) FileType.THUMBNAIL else FileType.FULL_IMAGE, 1
            )
        }
        val saved = buildThreadSaveSavedPost(aimogePost, resolveThreadSavePostMedia(aimogePost, files), emptyMap(), files)
        val futaba = buildThreadSaveSavedPost(futabaPost, resolveThreadSavePostMedia(futabaPost, emptyMap()), emptyMap())
        val metadata = SavedThreadMetadata(
            threadId = "t", boardId = "aimoge-aimg", boardName = "a", boardUrl = "https://nijiurachan.net/b/aimg/",
            title = "t", savedAt = 0L, expiresAtLabel = null, posts = listOf(saved, futaba), totalSize = 0L
        )
        val page = metadata.toThreadPage(InMemoryFileSystem(), baseDirectory = "/save")
        assertEquals(3, page.posts[0].attachments.size)
        assertEquals(true, page.posts[0].attachments[2].url.endsWith("b/c.png"))
        assertEquals(emptyList(), page.posts[1].attachments)
    }
}
