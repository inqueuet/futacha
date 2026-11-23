package com.valoser.futacha.shared.network.aimoge

import com.valoser.futacha.shared.compat.isAimogeNewsBoard
import com.valoser.futacha.shared.model.FileType
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.model.toThreadPage
import com.valoser.futacha.shared.repo.aimoge.AimogeBoardRepository
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.repo.aimoge.withAimogeAutomaticFetch
import com.valoser.futacha.shared.service.ThreadSaveLocalFileInfo
import com.valoser.futacha.shared.service.ThreadSaveMediaRequestType
import com.valoser.futacha.shared.service.buildThreadSaveMediaDownloadKey
import com.valoser.futacha.shared.service.buildThreadSaveSavedPost
import com.valoser.futacha.shared.service.buildThreadSaveMediaDownloadPlan
import com.valoser.futacha.shared.service.resolveThreadSavePostMedia
import com.valoser.futacha.shared.ui.compat.resolveCompatCatalogPreviewUrl
import com.valoser.futacha.shared.ui.ftutter.FtutterThumbnailSources
import com.valoser.futacha.shared.ui.ftutter.ftutterThumbnailSources
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AimogeNewsPreviewTest {
    private val id = "01a1f836-382c-771a-a8e6-cd3b6e8673db"
    private val threadUrl = "https://nijiurachan.net/b/news/thread/$id"
    private val articleImage = "https://publisher.example/thumb/article.png?w=1200"
    private val attachmentThumb = "https://storage.nijiurachan.net/aimg/thumbnails/photo.webp"
    private val attachmentImage = "https://storage.nijiurachan.net/aimg/attachments/photo.jpg"
    private val news = """"news":{"imageUrl":"$articleImage"}"""
    private val post = """{"id":"$id","threadId":"$id","seq":0,"status":"public","attachment":null,"attachments":[],$news}"""

    @Test fun catalogAndThreadUseTheArticleImageWhenThePostHasNoAttachment() {
        val catalog = decodeAimogeCatalog("""{"threads":[{"id":"$id","opPost":$post}]}""")
        val item = catalog.threads.single().toCatalogItem(threadUrl, "news")
        assertEquals(articleImage, item.thumbnailUrl)
        assertEquals(articleImage, item.fullImageUrl)

        val thread = decodeAimogeThread("""{"id":"$id","posts":[$post]}""")
        val snapshot = decodeAimogeSnapshot("""{"thread":{"id":"$id"},"posts":[$post]}""")
        for (posts in listOf(thread.posts, snapshot.posts)) {
            val result = aimogePosts(posts, "news").single()
            assertEquals(articleImage, result.thumbnailUrl)
            assertEquals(articleImage, result.imageUrl)
            assertTrue(result.attachments.isEmpty())
        }
    }

    @Test fun archiveAndPostSearchUseTheArticleImage() {
        val archive = """{"threads":[{"threadId":"$id","thumbnailUrl":null,$news}]}"""
        val search = """{"posts":[$post]}"""
        assertEquals(articleImage, parseAimogeArchiveSearch(archive, "news").single().thumbUrl)
        assertEquals(articleImage, parseAimogePostSearch(search, "news").single().thumbUrl)
        assertEquals(articleImage, parseAimogePostSearchRows(search, "news").single().thumbnailUrl)
    }

    @Test fun otherBoardsIgnoreNewsMetadataOnEveryReadPath() {
        val slug = "aimg"
        val url = "https://nijiurachan.net/b/$slug/thread/$id"
        val catalog = decodeAimogeCatalog("""{"threads":[{"id":"$id","opPost":$post}]}""")
        val thread = decodeAimogeThread("""{"id":"$id","posts":[$post]}""")
        val snapshot = decodeAimogeSnapshot("""{"thread":{"id":"$id"},"posts":[$post]}""")
        val archive = """{"threads":[{"threadId":"$id","thumbnailUrl":null,$news}]}"""
        val search = """{"posts":[$post]}"""
        assertNull(catalog.threads.single().toCatalogItem(url, slug).thumbnailUrl)
        assertNull(aimogeThreadPage(thread, thread.posts, 0, slug).posts.single().imageUrl)
        assertNull(aimogeThreadPage(checkNotNull(snapshot.thread), snapshot.posts, 0, slug).posts.single().imageUrl)
        assertNull(parseAimogeArchiveSearch(archive, slug).single().thumbUrl)
        assertNull(parseAimogePostSearch(search, slug).single().thumbUrl)
        assertNull(parseAimogePostSearchRows(search, slug).single().thumbnailUrl)
    }

    @Test fun aCachedNewsThreadCannotSupplyAnImageToAnotherBoard() = runTest {
        val newsBoard = "https://nijiurachan.net/b/news/"
        val otherBoard = "https://nijiurachan.net/b/aimg/"
        val client = HttpClient(MockEngine { respond("""{"id":"$id","posts":[$post]}""") })
        try {
            val repository = AimogeBoardRepository(client, nowMillis = { 0L })
            assertEquals(articleImage, repository.getThread(newsBoard, id).posts.single().imageUrl)
            assertEquals(articleImage, repository.fetchOpImageUrl(newsBoard, id))
            assertNull(repository.fetchOpImageUrl(otherBoard, id))
            val other = withAimogeAutomaticFetch { repository.getThread(otherBoard, id) }
            assertNull(other.posts.single().imageUrl)
        } finally {
            client.close()
        }
    }

    @Test fun unexpectedNewsShapesDoNotBreakOtherwiseValidPosts() {
        for (metadata in listOf("""["unexpected"]""", """{"imageUrl":{}}""")) {
            val malformedPost = post.replace(news, """"news":$metadata""")
            val thread = decodeAimogeThread("""{"id":"$id","posts":[$malformedPost]}""")
            assertNull(aimogeThreadPage(thread, thread.posts, 0, "aimg").posts.single().imageUrl)
            assertNull(aimogeThreadPage(thread, thread.posts, 0, "news").posts.single().imageUrl)
        }
    }

    @Test fun catalogQualityPreservesNewsUrlsAndKeepsOtherBoardsPolicy() {
        val op = AimogePostDto(id = id, news = newsImage(articleImage))
        val item = AimogeThreadDto(id = id, opPost = op).toCatalogItem(threadUrl, "news")
        val futabaItem = item.copy(threadUrl = "https://may.2chan.net/b/res/123.htm")
        val catVariant = articleImage.replace("/thumb/", "/cat/")
        assertEquals(articleImage, resolveCompatCatalogPreviewUrl(item, lowQuality = true))
        assertEquals(catVariant, resolveCompatCatalogPreviewUrl(futabaItem, lowQuality = true))
        assertEquals(
            FtutterThumbnailSources(articleImage, null),
            ftutterThumbnailSources(item.thumbnailUrl, item.fullImageUrl, 0, true,
                catalog = true, rewriteThumbnailPath = !isAimogeNewsBoard(item.threadUrl))
        )
        assertEquals(
            FtutterThumbnailSources(catVariant, null),
            ftutterThumbnailSources(futabaItem.thumbnailUrl, futabaItem.fullImageUrl, 0, true, catalog = true)
        )
    }

    @Test fun newsPreviewUsesOneSaveSlotAndLeavesRoomForTheNextImage() {
        val op = AimogePostDto(id = id, news = newsImage(articleImage)).toPost(0, "news")
        val reply = op.copy(id = "2", imageUrl = attachmentImage, thumbnailUrl = null)
        val plan = buildThreadSaveMediaDownloadPlan(listOf(op, reply), 2, "https://nijiurachan.net/b/news/")
        assertEquals(2, plan.totalMediaCount)
        assertEquals(listOf(articleImage, attachmentImage), plan.scheduledItems.map { it.url })
        assertEquals(ThreadSaveMediaRequestType.FULL_IMAGE, plan.scheduledItems.first().requestType)
        val thumbnails = buildThreadSaveMediaDownloadPlan(listOf(op), 1, "https://nijiurachan.net/b/news/",
            downloadFullMedia = false)
        assertEquals(1, thumbnails.totalMediaCount)
        assertEquals(ThreadSaveMediaRequestType.THUMBNAIL, thumbnails.scheduledItems.single().requestType)

        val otherBoard = buildThreadSaveMediaDownloadPlan(listOf(op, reply), 2, "https://nijiurachan.net/b/aimg/")
        assertEquals(3, otherBoard.totalMediaCount)
        assertEquals(listOf(articleImage, articleImage), otherBoard.scheduledItems.map { it.url })
    }

    @Test fun reopeningAFullSaveUsesTheSingleLocalFileForBothImageSizes() {
        val post = AimogePostDto(id = id, news = newsImage(articleImage)).toPost(0, "news")
        val localFile = "news/src/article.png"
        val fileMap = mapOf(
            buildThreadSaveMediaDownloadKey(articleImage, ThreadSaveMediaRequestType.FULL_IMAGE) to
                ThreadSaveLocalFileInfo(relativePath = localFile, fileType = FileType.FULL_IMAGE, byteSize = 42)
        )
        val saved = buildThreadSaveSavedPost(post, resolveThreadSavePostMedia(post, fileMap), emptyMap())
        assertEquals(localFile, saved.localImagePath)
        assertNull(saved.localThumbnailPath)
        val metadata = SavedThreadMetadata(id, "aimoge-news", "ニュー速", "https://nijiurachan.net/b/news/",
            title = "記事", savedAt = 0, expiresAtLabel = null, posts = listOf(saved), totalSize = 42)
        val reopened = metadata.toThreadPage(InMemoryFileSystem()).posts.single()
        assertTrue(reopened.imageUrl.orEmpty().endsWith("/$localFile"))
        assertEquals(reopened.imageUrl, reopened.thumbnailUrl)
    }

    @Test fun newsMediaSaveUsesResponseFormat() {
        fun resolve(url: String, type: io.ktor.http.ContentType) =
            com.valoser.futacha.shared.service.resolveNewsSavedMediaExtension(url, type)
        assertEquals("jpg", resolve("https://publisher.example/image.php?id=1", io.ktor.http.ContentType.Image.JPEG))
        assertNull(resolve("https://publisher.example/image.jpg", io.ktor.http.ContentType.Text.Html))
    }

    private fun newsImage(url: String) = JsonObject(mapOf("imageUrl" to JsonPrimitive(url)))

    @Test fun normalAttachmentsKeepPriorityAndUnusableArticleUrlsAreIgnored() {
        val attachment = AimogeAttachmentDto(originalUrl = attachmentImage, thumbnailUrl = attachmentThumb)
        val op = AimogePostDto(id = id, attachment = attachment, news = newsImage(articleImage))
        assertEquals(attachmentThumb, AimogeThreadDto(id = id, opPost = op).toCatalogItem(threadUrl, "news").thumbnailUrl)
        assertEquals(attachmentImage, op.toPost(order = 0, boardSlug = "news").imageUrl)
        assertNull(op.copy(attachment = null, status = "unavailable").toPost(order = 0, boardSlug = "news").imageUrl)
        assertNull(aimogeNewsPreviewUrl("news", newsImage("http://publisher.example/image.png")))
        assertNull(aimogeNewsPreviewUrl("news", newsImage("https://")))
        assertNull(aimogeNewsPreviewUrl("news", newsImage("https://user@publisher.example/image.png")))
    }
}
