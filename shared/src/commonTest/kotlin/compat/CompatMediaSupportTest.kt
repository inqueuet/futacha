package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.ScrollAnchor
import com.valoser.futacha.shared.model.CatalogItem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import com.valoser.futacha.shared.media.FutabaMediaKind
import com.valoser.futacha.shared.media.FUTABA_COMPAT_IMAGE_EXTENSIONS
import com.valoser.futacha.shared.media.FUTABA_COMPAT_VIDEO_EXTENSIONS
import com.valoser.futacha.shared.media.classifyFutabaMedia
import com.valoser.futacha.shared.ui.image.suppressFutabaExtensionFallbackForUrl

class CompatMediaSupportTest {
    @Test
    fun apuSmallUrlsAreCanonicalAndNeverTriggerExtensionProbing() {
        val malformed = "https://dec.2chan.net/up2//src//fu1234567.PNG?x=1"
        val source = "https://dec.2chan.net/up2/src/fu1234567.PNG?x=1"
        val thumbnail = "https://dec.2chan.net/up2/thumb/fu1234567s.jpg"

        assertEquals(source, normalizeCompatApuSmallMediaUrl(malformed))
        assertTrue(isCompatApuSmallMediaUrl(malformed))
        assertEquals(thumbnail, compatApuSmallThumbnailUrl(malformed))
        assertTrue(suppressFutabaExtensionFallbackForUrl(source))
        assertTrue(suppressFutabaExtensionFallbackForUrl(thumbnail))
        assertFalse(suppressFutabaExtensionFallbackForUrl("https://may.2chan.net/b/src/1.jpg"))

        val normalized = normalizeCompatPostMedia(
            CompatPostSnapshot(
                position = 0,
                postNo = "1",
                timestamp = "now",
                messageHtml = "fu1234567.PNG",
                imageUrl = malformed
            )
        )
        assertEquals(source, normalized.imageUrl)
        assertNull(normalized.thumbnailUrl)
        assertEquals(source, resolveCompatPostPreviewUrl(normalized, "読み込む"))
    }

    @Test
    fun gallerySaveModeSelectsMediaForZipOrFolderBatchSave() {
        assertEquals(CompatGalleryTapAction.OPEN_VIEWER, compatGalleryTapAction(saveMode = false))
        assertEquals(CompatGalleryTapAction.SELECT_MEDIA, compatGalleryTapAction(saveMode = true))
        assertEquals(
            "ZIPに3件を保存しました\n1件失敗しました",
            buildCompatGalleryBatchSaveMessage(CompatGalleryBatchSaveFormat.ZIP, succeeded = 3, failed = 1)
        )
        assertEquals(
            "フォルダに2件を保存しました",
            buildCompatGalleryBatchSaveMessage(CompatGalleryBatchSaveFormat.FOLDER, succeeded = 2, failed = 0)
        )
        assertEquals(
            listOf(
                "https://may.2chan.net/b/src/1.jpg",
                "https://may.2chan.net/b/src/2.mp4",
                "https://may.2chan.net/b/src/3.webm"
            ),
            compatBatchMediaUrls(
                listOf(
                    CompatPostSnapshot(position = 0, postNo = "1", timestamp = "now", messageHtml = "", imageUrl = "https://may.2chan.net/b/src/1.jpg"),
                    CompatPostSnapshot(position = 1, postNo = "2", timestamp = "now", messageHtml = "", imageUrl = "https://may.2chan.net/b/src/2.mp4"),
                    CompatPostSnapshot(position = 2, postNo = "3", timestamp = "now", messageHtml = "", imageUrl = "https://may.2chan.net/b/src/3.webm"),
                    CompatPostSnapshot(position = 3, postNo = "4", timestamp = "now", messageHtml = "", imageUrl = "https://example.com/readme.txt"),
                    CompatPostSnapshot(position = 4, postNo = "5", timestamp = "now", messageHtml = "", imageUrl = "https://may.2chan.net/b/src/1.jpg")
                )
            )
        )
        assertEquals(
            mapOf(
                "https://may.2chan.net/b/src/1.mp4" to "1.mp4",
                "https://dec.2chan.net/up/src/1.mp4" to "1(1).mp4",
                "https://may.2chan.net/b/src/2.jpg?cache=1" to "2.jpg"
            ),
            compatBatchOutputFileNames(
                listOf(
                    "https://may.2chan.net/b/src/1.mp4",
                    "https://dec.2chan.net/up/src/1.mp4",
                    "https://may.2chan.net/b/src/2.jpg?cache=1"
                )
            )
        )
        assertEquals(listOf("表示オプション", "設定", "ヘルプ"), compatGalleryOverflowLabels())
        assertEquals(
            listOf("表示オプション", "ツールバー編集", "設定", "ヘルプ"),
            compatViewerTopOverflowLabels()
        )
        assertEquals(
            listOf(
                "NGスレッドに登録", "NGスレッドとNGワードに登録", "NG画像に登録",
                "delを送信する", "delとNGスレッドに登録",
                "delとNGスレッドとNGワードに登録", "タブに追加する"
            ),
            compatCatalogContextLabels()
        )
        assertEquals(
            listOf(
                "元レスに移動する", "画像を保存する", "サムネイルを再読み込みする",
                "NG画像に登録", "リンクURLをコピー", "ブラウザーで開く",
                "URLを共有", "画像を共有"
            ),
            compatGalleryContextBaseLabels()
        )
        assertEquals(compatGalleryContextBaseLabels().drop(1), compatThreadImageContextBaseLabels())
        assertEquals(listOf("保存", "共有", "検索"), compatViewerQuickMenuLabels())
        assertEquals(
            listOf(
                "お気に入り", "削除する", "下のスレを全て削除する",
                "他のスレを全て削除する", "落ちたスレを削除する", "全て削除する"
            ),
            compatDrawerTabContextLabels()
        )
    }

    @Test
    fun reverseImageSearchBrowserAcceptsOnlyBoundedHttpUrls() {
        assertTrue(isCompatReverseSearchBrowserUrl("https://lens.google.com/result?id=1"))
        assertTrue(isCompatReverseSearchBrowserUrl("http://ascii2d.net/search"))
        assertFalse(isCompatReverseSearchBrowserUrl("javascript:alert(1)"))
        assertFalse(isCompatReverseSearchBrowserUrl("file:///tmp/result.html"))
        assertFalse(isCompatReverseSearchBrowserUrl("https:///missing-host"))
        assertFalse(isCompatReverseSearchBrowserUrl("https://example.com/" + "x".repeat(8_192)))
        assertEquals(
            "https://example.com/result?id=1",
            normalizeCompatReverseSearchLongPressedLink("  https://example.com/result?id=1  ")
        )
        assertNull(normalizeCompatReverseSearchLongPressedLink("javascript:alert(1)"))
        assertEquals(
            listOf("外部ブラウザで開く", "URLをコピー"),
            compatReverseSearchLinkMenuItems("https://example.com/result").map { it.label }
        )
        assertTrue(compatReverseSearchLinkMenuItems("file:///tmp/result").isEmpty())
    }

    @Test
    fun apngMarkerDetectionIsBoundedAndStopsBeforeImageData() {
        fun chunk(type: String, payload: ByteArray = byteArrayOf()): ByteArray {
            val size = payload.size
            return byteArrayOf(
                (size ushr 24).toByte(), (size ushr 16).toByte(),
                (size ushr 8).toByte(), size.toByte()
            ) + type.encodeToByteArray() + payload + byteArrayOf(0, 0, 0, 0)
        }
        val signature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        )
        assertTrue(isCompatApngHeader(signature + chunk("IHDR", ByteArray(13)) + chunk("acTL", ByteArray(8))))
        assertFalse(isCompatApngHeader(signature + chunk("IHDR", ByteArray(13)) + chunk("IDAT") + chunk("acTL")))
        assertFalse(isCompatApngHeader("not-png".encodeToByteArray()))
    }

    @Test
    fun futabaMediaRegistry_acceptsAllSupportedImageAndVideoExtensions() {
        FUTABA_COMPAT_IMAGE_EXTENSIONS.forEach { extension ->
            assertEquals(
                FutabaMediaKind.IMAGE,
                classifyFutabaMedia("https://img.2chan.net/b/src/1.$extension?cache=1#x")
            )
        }
        FUTABA_COMPAT_VIDEO_EXTENSIONS.forEach { extension ->
            assertEquals(
                FutabaMediaKind.VIDEO,
                classifyFutabaMedia("https://may.2chan.net/b/src/1.$extension?cache=1#x")
            )
        }
        assertEquals(FutabaMediaKind.IMAGE, classifyFutabaMedia("https://img.2chan.net/b/src/1.PNG"))
        assertEquals(FutabaMediaKind.IMAGE, classifyFutabaMedia("https://img.2chan.net/b/src/1", "image/png"))
        assertEquals(FutabaMediaKind.VIDEO, classifyFutabaMedia("https://may.2chan.net/b/src/1", "video/mp4"))
    }

    @Test
    fun compatibilityImageRequestFallbackPolicy_neverFallsBackToVideo() {
        val policy = com.valoser.futacha.shared.ui.image.FutabaExtensionFallbackPolicy(
            maxAttempts = 5,
            allowVideoFallback = false,
            preferStaticCandidates = true,
            maxVideoAttempts = 0
        )
        assertFalse(policy.allowVideoFallback)
        assertEquals(0, policy.maxVideoAttempts)
    }

    @Test
    fun fixtureCatalogPreview_hasPackagedFallbackAfterRemoteCandidates() {
        val item = CatalogItem(
            id = "1364612020",
            threadUrl = "https://dat.example.com/t/res/1364612020.htm",
            title = "チュートリアル",
            thumbnailUrl = "https://dat.example.com/t/thumb/1762436883775s.jpg",
            fullImageUrl = "https://dat.example.com/t/src/1762436883775.jpg",
            replyCount = 0
        )

        assertEquals(
            listOf(
                item.thumbnailUrl,
                item.fullImageUrl,
                "android.resource://com.valoser.futacha/drawable/compat_fixture_catalog_thumb"
            ),
            compatCatalogPreviewCandidates(item)
        )
        assertEquals(
            listOf(
                "https://dat.example.com/t/cat/1762436883775s.jpg",
                item.thumbnailUrl,
                item.fullImageUrl,
                "android.resource://com.valoser.futacha/drawable/compat_fixture_catalog_thumb"
            ),
            compatCatalogPreviewCandidates(item, lowQuality = true)
        )
    }

    @Test
    fun catalogLowQualityUsesSeparateCatUrlAndReturnsToThumb() {
        val item = CatalogItem(
            id = "901",
            threadUrl = "https://may.2chan.net/b/res/901.htm",
            title = "quality",
            thumbnailUrl = "https://may.2chan.net/b/thumb/901s.jpg",
            fullImageUrl = "https://may.2chan.net/b/src/901.png",
            replyCount = 2
        )

        assertEquals(item.thumbnailUrl, resolveCompatCatalogPreviewUrl(item, lowQuality = false))
        assertEquals(
            "https://may.2chan.net/b/cat/901s.jpg",
            resolveCompatCatalogPreviewUrl(item, lowQuality = true)
        )
        assertEquals(
            listOf("https://may.2chan.net/b/cat/901s.jpg", item.thumbnailUrl, item.fullImageUrl),
            compatCatalogPreviewCandidates(item, lowQuality = true)
        )
    }

    @Test
    fun catalogMobileEcoUsesTheFinalApkMeteredPolicy() {
        assertFalse(
            shouldUseCompatCatalogLowQuality(
                alwaysLowQuality = false,
                meteredOnlyLowQuality = false,
                isUnmeteredConnection = false
            )
        )
        assertTrue(
            shouldUseCompatCatalogLowQuality(
                alwaysLowQuality = false,
                meteredOnlyLowQuality = true,
                isUnmeteredConnection = false
            )
        )
        assertFalse(
            shouldUseCompatCatalogLowQuality(
                alwaysLowQuality = false,
                meteredOnlyLowQuality = true,
                isUnmeteredConnection = true
            )
        )
        assertTrue(
            shouldUseCompatCatalogLowQuality(
                alwaysLowQuality = true,
                meteredOnlyLowQuality = false,
                isUnmeteredConnection = true
            )
        )
    }

    @Test
    fun viewerPreloadUsesFinalApkRawValuesAndReadsLegacyLabels() {
        assertTrue(shouldPreloadCompatViewer("usually", isUnmeteredConnection = false))
        assertTrue(shouldPreloadCompatViewer(null, isUnmeteredConnection = false))
        assertTrue(shouldPreloadCompatViewer("wifi", isUnmeteredConnection = true))
        assertFalse(shouldPreloadCompatViewer("wifi", isUnmeteredConnection = false))
        assertFalse(shouldPreloadCompatViewer("none", isUnmeteredConnection = true))

        assertTrue(shouldPreloadCompatViewer("常に利用する", isUnmeteredConnection = false))
        assertTrue(shouldPreloadCompatViewer("Wi-Fi回線のみ", isUnmeteredConnection = true))
        assertFalse(shouldPreloadCompatViewer("Wi-Fi回線のみ", isUnmeteredConnection = false))
        assertFalse(shouldPreloadCompatViewer("利用しない", isUnmeteredConnection = true))
    }

    @Test
    fun threadThumbnailBounds_matchLegacyMaxEdgeSizing() {
        assertEquals(250 to 125, compatThreadThumbnailBounds(250, 1600, 800))
        assertEquals(125 to 250, compatThreadThumbnailBounds(250, 800, 1600))
        assertEquals(250 to 250, compatThreadThumbnailBounds(250, null, null))
    }

    @Test
    fun thumbnailDecodeBoundMatchesRenderedPhysicalPixelsWithoutRoundingDown() {
        assertEquals(250, compatThumbnailRequestSizePx(displaySizeDp = 100f, density = 2.5f))
        assertEquals(227, compatThumbnailRequestSizePx(displaySizeDp = 86.4f, density = 2.625f))
        assertEquals(1, compatThumbnailRequestSizePx(displaySizeDp = 0f, density = 3f))
    }

    @Test
    fun apuSmallThumbnailBoundsUseDecodedLandscapeAspectRatio() {
        assertEquals(250 to 140, compatThreadThumbnailBounds(250, 1920, 1080))
        assertEquals(140 to 250, compatThreadThumbnailBounds(250, 1080, 1920))
    }

    @Test
    fun persistedArchiveApuLabelsAreNormalizedBeforePresentation() {
        val sourceUrl = "https://dec.2chan.net/up2/src/fu7190971.png"
        val body = normalizeCompatPostMedia(
            CompatPostSnapshot(
                position = 191,
                postNo = "1463510009",
                timestamp = "26/08/30(日)12:09:25",
                messageHtml =
                    "<a href=\"$sourceUrl\">fu7190971.png</a>" +
                        "<span onclick=\"previewImg('body','$sourceUrl')\">[見る]</span><br>りんみ"
            )
        )
        val quote = normalizeCompatPostMedia(
            CompatPostSnapshot(
                position = 192,
                postNo = "1463510029",
                timestamp = "26/08/30(日)12:09:30",
                messageHtml =
                    "&gt;<a href=\"$sourceUrl\">fu7190971.png</a>" +
                        "<span onclick=\"previewImg('quote','$sourceUrl')\">[見る]</span><br>失恋はほむらもだろ…"
            )
        )

        assertEquals(
            "<a href=\"$sourceUrl\">fu7190971.png</a><br>りんみ",
            body.messageHtml
        )
        assertEquals(
            "&gt;<a href=\"$sourceUrl\">fu7190971.png</a><br>失恋はほむらもだろ…",
            quote.messageHtml
        )
        assertEquals(
            listOf("1463510009"),
            compatViewerMediaPosts(
                posts = listOf(body, quote),
                upsThumbnailMethod = "表示する"
            ).map { it.postNo }
        )
    }

    @Test
    fun remoteInfo_usesHeadWithoutDownloadingMediaBody() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Head, request.method)
            respond(
                content = "",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("image/webp; charset=binary"),
                    HttpHeaders.ContentLength to listOf("8388608")
                )
            )
        })

        val result = fetchCompatRemoteMediaInfo(client, "https://may.2chan.net/b/src/test.webp").getOrThrow()

        assertEquals("image/webp", result.contentType)
        assertEquals(8_388_608L, result.contentLengthBytes)
        assertEquals("8.0 MB (8388608 bytes)", formatCompatMediaByteSize(result.contentLengthBytes))
        client.close()
    }

    @Test
    fun remoteInfo_rejectsFailedStatusAndFormattingHandlesUnknown() = runBlocking {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.MethodNotAllowed) })

        assertTrue(fetchCompatRemoteMediaInfo(client, "https://example.invalid/a.jpg").isFailure)
        assertEquals("不明", formatCompatMediaByteSize(null))
        assertEquals("100 B", formatCompatMediaByteSize(100L))
        assertEquals("2 KB (2048 bytes)", formatCompatMediaByteSize(2048L))
        client.close()
    }

    @Test
    fun exifSummary_readsJpegTiffHeaderWithoutDecodingTheImage() {
        fun b(vararg values: Int): List<Byte> = values.map(Int::toByte)
        val tiff = mutableListOf<Byte>().apply {
            addAll(b('I'.code, 'I'.code, 42, 0, 8, 0, 0, 0))
            addAll(b(2, 0))
            // Orientation = 1 (short, inline).
            addAll(b(0x12, 0x01, 3, 0, 1, 0, 0, 0, 1, 0, 0, 0))
            // Make = "ACME" (ASCII, offset 38 in this TIFF).
            addAll(b(0x0F, 0x01, 2, 0, 5, 0, 0, 0, 38, 0, 0, 0))
            addAll(b(0, 0, 0, 0))
            addAll(b('A'.code, 'C'.code, 'M'.code, 'E'.code, 0))
        }
        val exif = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0) + tiff.toByteArray()
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(),
            ((exif.size + 2) shr 8).toByte(), (exif.size + 2).toByte()) + exif +
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())

        val summary = parseCompatExifSummary(jpeg)

        assertTrue(summary.contains("メーカー: ACME"), summary)
        assertTrue(summary.contains("向き: 1"), summary)
    }

    @Test
    fun exifSummary_rejectsOverflowingValueRangeWithoutCrashing() {
        fun b(vararg values: Int): List<Byte> = values.map(Int::toByte)
        val tiff = mutableListOf<Byte>().apply {
            addAll(b('I'.code, 'I'.code, 42, 0, 8, 0, 0, 0))
            addAll(b(1, 0))
            // ASCII count is Int.MAX_VALUE. Adding it to the absolute value
            // offset used to wrap negative before copyOfRange().
            addAll(b(0x0F, 0x01, 2, 0, 0xFF, 0xFF, 0xFF, 0x7F, 8, 0, 0, 0))
            addAll(b(0, 0, 0, 0))
        }
        val exif = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0) + tiff.toByteArray()
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(),
            ((exif.size + 2) shr 8).toByte(), (exif.size + 2).toByte()
        ) + exif + byteArrayOf(0xFF.toByte(), 0xD9.toByte())

        assertEquals("なし", parseCompatExifSummary(jpeg))
    }

    @Test
    fun mediaByteSizeFormatting_doesNotOverflowForLargeHeaders() {
        assertEquals(
            "8796093022207.9 MB (9223372036854775807 bytes)",
            formatCompatMediaByteSize(Long.MAX_VALUE)
        )
    }

    @Test
    fun exifSummary_readsCameraFieldsFromExifSubIfd() {
        fun b(vararg values: Int): List<Byte> = values.map(Int::toByte)
        val tiff = mutableListOf<Byte>().apply {
            // Little-endian TIFF, primary IFD at offset 8.
            addAll(b('I'.code, 'I'.code, 42, 0, 8, 0, 0, 0))
            addAll(b(2, 0))
            // ExifIFDPointer = 38 (type LONG, inline value).
            addAll(b(0x69, 0x87, 4, 0, 1, 0, 0, 0, 38, 0, 0, 0))
            // Make = "ACME" at offset 116.
            addAll(b(0x0F, 0x01, 2, 0, 5, 0, 0, 0, 116, 0, 0, 0))
            addAll(b(0, 0, 0, 0)) // next IFD
            // Exif sub-IFD at offset 38: FNumber=2.8, Exposure=1/125,
            // ISO=400, FocalLength=50mm.
            addAll(b(4, 0))
            addAll(b(0x9D, 0x82, 5, 0, 1, 0, 0, 0, 92, 0, 0, 0))
            addAll(b(0x9A, 0x82, 5, 0, 1, 0, 0, 0, 100, 0, 0, 0))
            addAll(b(0x27, 0x88, 3, 0, 1, 0, 0, 0, 400, 1, 0, 0))
            addAll(b(0x0A, 0x92, 5, 0, 1, 0, 0, 0, 108, 0, 0, 0))
            addAll(b(0, 0, 0, 0))
            addAll(b(28, 0, 0, 0, 10, 0, 0, 0)) // 2.8
            addAll(b(1, 0, 0, 0, 125, 0, 0, 0)) // 1/125
            addAll(b(50, 0, 0, 0, 1, 0, 0, 0)) // 50/1
            addAll(b('A'.code, 'C'.code, 'M'.code, 'E'.code, 0))
        }
        val exif = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0) + tiff.toByteArray()
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(),
            ((exif.size + 2) shr 8).toByte(), (exif.size + 2).toByte()
        ) + exif + byteArrayOf(0xFF.toByte(), 0xD9.toByte())

        val summary = parseCompatExifSummary(jpeg)

        assertTrue(summary.contains("絞り値: f/2.8"), summary)
        assertTrue(summary.contains("露出時間: 1/125 秒"), summary)
        assertTrue(summary.contains("ISO 感度: 400"), summary)
        assertTrue(summary.contains("焦点距離: 50 mm"), summary)
        assertTrue(summary.contains("メーカー: ACME"), summary)
    }

    @Test
    fun externalUploaderSourceWithoutThumbnail_isReachableFromPostAndViewer() {
        val post = CompatPostSnapshot(
            position = 0,
            postNo = "801",
            timestamp = "26/08/05(水)20:00:00",
            messageHtml = "fu7072538.jpg",
            imageUrl = "https://dec.2chan.net/up2/src/fu7072538.jpg",
            thumbnailUrl = null
        )

        assertEquals(post.imageUrl, resolveCompatPostPreviewUrl(post, upsThumbnailMethod = "表示する"))
        assertEquals(post.imageUrl, resolveCompatViewerMediaUrl(post))
    }

    @Test
    fun externalUploaderImageWithoutThumbnailKeepsOneStableSourceRequest() {
        val normalized = normalizeCompatPostMedia(
            CompatPostSnapshot(
                position = 0,
                postNo = "801b",
                timestamp = "26/08/05(水)20:00:00",
                messageHtml = "fu7099123.jpg",
                imageUrl = "https://dec.2chan.net/up2/src/fu7099123.jpg"
            )
        )

        assertNull(normalized.thumbnailUrl)
        assertEquals(normalized.imageUrl, resolveCompatPostPreviewUrl(normalized, "読み込む"))
    }

    @Test
    fun cachedAbsolutePngLink_isRecoveredIntoViewerMedia() {
        val normalized = normalizeCompatPostMedia(
            CompatPostSnapshot(
                position = 0,
                postNo = "801png",
                timestamp = "26/08/05(水)20:00:00",
                messageHtml = "画像 <a href=\"https://img.2chan.net/b/src/801.png?cache=1\">801.png</a>"
            )
        )

        assertEquals("https://img.2chan.net/b/src/801.png?cache=1", normalized.imageUrl)
        assertEquals(
            listOf("801png"),
            compatViewerMediaPosts(listOf(normalized)).map { it.postNo }
        )
        assertEquals(FutabaMediaKind.IMAGE, classifyFutabaMedia(normalized.imageUrl))
    }

    @Test
    fun commonFutabaUploaderImageLink_isRecoveredIntoViewerMedia() {
        val source = "http://www.nijibox6.com/futabafiles/001/src/sa1234567.jpg"
        val normalized = normalizeCompatPostMedia(
            CompatPostSnapshot(
                position = 0,
                postNo = "801sio",
                timestamp = "",
                messageHtml = "<a href=\"$source\">sa1234567.jpg</a>"
            )
        )

        assertEquals(source, normalized.imageUrl)
        assertTrue(compatViewerPostMatchesMediaUrl(normalized, source))
        assertEquals(
            listOf("801sio"),
            compatViewerMediaPosts(listOf(normalized)).map { it.postNo }
        )
    }

    @Test
    fun inlineUploaderLinksRemainAvailableWhenPostHasBoardAttachment() {
        assertEquals(
            listOf("https://dec.2chan.net/up2/src/fu7099123.jpg"),
            compatInlineApuSmallMediaUrls("本文<br>fu7099123.jpg")
        )
    }

    @Test
    fun inlineUploaderPreviewHonorsDisabledSettingWithoutChangingBodyLinks() {
        val body = "fu7099123.jpg<br>https://dec.2chan.net/up2/src/fu7099124.png"

        assertEquals(
            emptyList(),
            compatVisibleInlineApuSmallMediaUrls(
                messageHtml = body,
                upsThumbnailMethod = "表示しない",
                wifiConnected = true
            )
        )
        assertEquals(
            listOf(
                "https://dec.2chan.net/up2/src/fu7099123.jpg",
                "https://dec.2chan.net/up2/src/fu7099124.png"
            ),
            compatVisibleInlineApuSmallMediaUrls(
                messageHtml = body,
                upsThumbnailMethod = "表示する",
                wifiConnected = false
            )
        )
    }

    @Test
    fun bareUploaderFilenamesAreAllAvailableForPreviewGalleryAndViewer() {
        val body = "fu7099123.jpg<br>fu7099124.png<br>通常の本文"

        assertEquals(
            listOf(
                "https://dec.2chan.net/up2/src/fu7099123.jpg",
                "https://dec.2chan.net/up2/src/fu7099124.png"
            ),
            compatInlineApuSmallMediaUrls(body)
        )

        val normalized = normalizeCompatPostMedia(
            CompatPostSnapshot(
                position = 0,
                postNo = "811",
                timestamp = "",
                messageHtml = body
            )
        )
        assertEquals(
            listOf("811", "811"),
            compatViewerMediaPosts(
                listOf(normalized),
                upsThumbnailMethod = "読み込む"
            ).map { it.postNo }
        )
        assertEquals(
            listOf(
                "https://dec.2chan.net/up2/src/fu7099123.jpg",
                "https://dec.2chan.net/up2/src/fu7099124.png"
            ),
            compatVisibleInlineApuSmallMediaUrls(body, "表示する", wifiConnected = true)
        )
    }

    @Test
    fun inlineUploaderImageIsAddedToGalleryAndViewerWithStableMediaIdentity() {
        val post = CompatPostSnapshot(
            position = 0,
            postNo = "808",
            timestamp = "26/08/05(水)20:03:00",
            messageHtml = "本文<br>fu7099123.jpg",
            imageUrl = "https://img.2chan.net/b/src/normal.jpg",
            thumbnailUrl = "https://img.2chan.net/b/thumb/normals.jpg"
        )

        val media = compatViewerMediaPosts(
            listOf(post),
            upsThumbnailMethod = "表示する"
        )

        assertEquals(2, media.size)
        assertEquals("https://img.2chan.net/b/src/normal.jpg", media[0].imageUrl)
        assertEquals("https://dec.2chan.net/up2/src/fu7099123.jpg", media[1].imageUrl)
        assertEquals("808::apu::0", compatMediaIdentity(media[1]))
        assertEquals(1, compatViewerInitialPage(media, "808::apu::0", 0))
    }

    @Test
    fun previewsPreferThumbnailAndViewerPrefersOriginal() {
        val post = CompatPostSnapshot(
            position = 0,
            postNo = "802",
            timestamp = "26/08/05(水)20:01:00",
            messageHtml = "video",
            imageUrl = "https://may.2chan.net/b/src/1785929695781.webm",
            thumbnailUrl = "https://may.2chan.net/b/thumb/1785929695781s.jpg"
        )
        val catalog = CatalogItem(
            id = "802",
            threadUrl = "https://may.2chan.net/b/res/802.htm",
            title = "webp",
            thumbnailUrl = "https://may.2chan.net/b/thumb/1785925785783s.jpg",
            fullImageUrl = "https://may.2chan.net/b/src/1785925785783.webp",
            replyCount = 1
        )

        assertEquals(post.thumbnailUrl, resolveCompatPostPreviewUrl(post))
        assertEquals(post.imageUrl, resolveCompatViewerMediaUrl(post))
        assertEquals(catalog.thumbnailUrl, resolveCompatCatalogPreviewUrl(catalog))
        assertEquals(
            "https://may.2chan.net/b/cat/1785925785783s.jpg",
            resolveCompatCatalogPreviewUrl(catalog, lowQuality = true)
        )
        assertTrue(isCompatVideoMediaUrl(post.imageUrl!!))
        assertFalse(isCompatVideoMediaUrl(catalog.fullImageUrl!!))
    }

    @Test
    fun viewerLaunchPrefersPostIdentityOverStaleIndex() {
        val posts = listOf(
            CompatPostSnapshot(0, "901", timestamp = "", messageHtml = "", imageUrl = "a"),
            CompatPostSnapshot(1, "902", timestamp = "", messageHtml = "", imageUrl = "b"),
            CompatPostSnapshot(2, "903", timestamp = "", messageHtml = "", imageUrl = "c")
        )

        assertEquals(2, compatViewerInitialPage(posts, requestedPostNo = "903", fallbackIndex = 0))
        assertEquals(1, compatViewerInitialPage(posts, requestedPostNo = "missing", fallbackIndex = 1))
        assertEquals(0, compatViewerInitialPage(emptyList(), requestedPostNo = "901", fallbackIndex = 2))
    }

    @Test
    fun viewerToolbarKeepsSourcePostAndGalleryDestinationsSeparate() {
        val posts = listOf(
            CompatPostSnapshot(17, "901", timestamp = "", messageHtml = "", imageUrl = "a"),
            CompatPostSnapshot(42, "902", timestamp = "", messageHtml = "", imageUrl = "b")
        )

        assertEquals(
            CompatViewerNavigationTarget.SourcePost(
                ScrollAnchor(postNo = "902", fallbackIndex = 42, snapshotRevision = 8L)
            ),
            compatViewerNavigationTarget("back", posts, currentPage = 1, snapshotRevision = 8L)
        )
        assertEquals(
            CompatViewerNavigationTarget.Gallery(index = 1, mediaIdentity = "902"),
            compatViewerNavigationTarget("gallery", posts, currentPage = 1, snapshotRevision = 8L)
        )
    }

    @Test
    fun legacyApuSmallSourceFollowsDisplaySetting() {
        val url = "https://dec.2chan.net/up2/src/fu1234567.jpg"
        val post = CompatPostSnapshot(
            position = 0,
            postNo = "803",
            timestamp = "26/08/05(水)20:02:00",
            messageHtml = "fu1234567.jpg",
            imageUrl = url
        )
        assertTrue(isCompatApuSmallMediaUrl(url))
        assertEquals("https://dec.2chan.net/up2/thumb/fu1234567s.jpg", compatApuSmallThumbnailUrl(url))
        assertFalse(compatPostHasVisibleMedia(post, "利用しない", wifiConnected = true))
        assertNull(resolveCompatPostPreviewUrl(post, "利用しない", wifiConnected = true))
        assertTrue(compatPostHasVisibleMedia(post, "読み込む", wifiConnected = false))
        assertFalse(compatPostHasVisibleMedia(post, "Wi-Fi回線のみ", wifiConnected = false))
        assertEquals(url, resolveCompatPostPreviewUrl(post, "先読みする", wifiConnected = false))
    }

    @Test
    fun legacyNormalUpSourceUsesTheSameThumbnailConvention() {
        val url = "https://dec.2chan.net/up/src/f1234567.png"
        assertTrue(isCompatApuSmallMediaUrl(url))
        assertEquals("https://dec.2chan.net/up/thumb/f1234567s.jpg", compatApuSmallThumbnailUrl(url))
    }

    @Test
    fun bareApuSmallFilenameGetsSourceForCachedPosts() {
        val normalized = normalizeCompatPostMedia(
            CompatPostSnapshot(
                position = 0,
                postNo = "804",
                timestamp = "",
                messageHtml = "fu7085829.jpg<br>あぷ小テスト"
            )
        )

        assertEquals("https://dec.2chan.net/up2/src/fu7085829.jpg", normalized.imageUrl)
        assertNull(normalized.thumbnailUrl)
        assertEquals(normalized.imageUrl, resolveCompatPostPreviewUrl(normalized, "読み込む"))
        assertEquals(
            listOf("804"),
            compatViewerMediaPosts(listOf(normalized), upsThumbnailMethod = "読み込む")
                .map { it.postNo }
        )
    }

    @Test
    fun cachedQuotedApuSmallFilenameDoesNotCreateDuplicateMedia() {
        val normalized = normalizeCompatPostMedia(
            CompatPostSnapshot(
                position = 0,
                postNo = "807",
                timestamp = "",
                messageHtml = "&gt;fu7085829.jpg<br>&gt;あぷ小テスト"
            )
        )

        assertEquals(null, normalized.imageUrl)
        assertEquals(null, normalized.thumbnailUrl)
        assertTrue(compatViewerMediaPosts(listOf(normalized), upsThumbnailMethod = "読み込む").isEmpty())
    }

    @Test
    fun quotedApuPrimarySlotIsRemovedFromViewerMedia() {
        val normalized = normalizeCompatPostMedia(
            CompatPostSnapshot(
                position = 0,
                postNo = "809",
                timestamp = "",
                messageHtml = ">fu7100611.jpg",
                imageUrl = "https://dec.2chan.net/up2/src/fu7100611.jpg",
                thumbnailUrl = "https://dec.2chan.net/up2/thumb/fu7100611s.jpg"
            )
        )

        assertEquals(null, normalized.imageUrl)
        assertEquals(null, normalized.thumbnailUrl)
        assertTrue(compatViewerMediaPosts(listOf(normalized)).isEmpty())
    }

    @Test
    fun viewerMediaPosts_keepPagerIndexAlignedWithVisibleThumbnails() {
        val posts = listOf(
            CompatPostSnapshot(position = 0, postNo = "1", timestamp = "", messageHtml = "text"),
            CompatPostSnapshot(
                position = 1,
                postNo = "2",
                timestamp = "",
                messageHtml = "image",
                imageUrl = "https://may.2chan.net/b/src/2.jpg",
                thumbnailUrl = "https://may.2chan.net/b/thumb/2s.jpg"
            ),
            CompatPostSnapshot(
                position = 2,
                postNo = "3",
                timestamp = "",
                messageHtml = "hidden",
                imageUrl = "https://may.2chan.net/b/src/3.jpg"
            ),
            CompatPostSnapshot(
                position = 3,
                postNo = "4",
                timestamp = "",
                messageHtml = "apu",
                imageUrl = "https://dec.2chan.net/up2/src/fu1234567.jpg"
            )
        )

        val visible = compatViewerMediaPosts(
            posts = posts,
            hiddenImages = setOf("https://may.2chan.net/b/src/3.jpg"),
            upsThumbnailMethod = "利用しない",
            wifiConnected = true
        )

        assertEquals(listOf("2"), visible.map { it.postNo })
        assertEquals(0, visible.indexOfFirst { it.postNo == "2" })
    }

    @Test
    fun bareApuSmallSourceIsExcludedFromGalleryWhenThumbnailLoadingIsDisabled() {
        val source = "https://dec.2chan.net/up2/src/fu7100605.jpg"
        val post = normalizeCompatPostMedia(
            CompatPostSnapshot(
                position = 0,
                postNo = "810",
                timestamp = "",
                messageHtml = "fu7100605.jpg",
                imageUrl = source
            )
        )

        assertFalse(compatPostHasVisibleMedia(post, "利用しない", wifiConnected = true))
        assertTrue(
            compatViewerMediaPosts(
                listOf(post),
                upsThumbnailMethod = "利用しない",
                wifiConnected = true
            ).isEmpty()
        )
        assertTrue(compatViewerPostMatchesMediaUrl(post, "$source?from=body#tap"))
    }

    @Test
    fun uploaderGalleryPolicyMatchesReferenceSettings() {
        val post = normalizeCompatPostMedia(
            CompatPostSnapshot(
                position = 0,
                postNo = "810b",
                timestamp = "",
                messageHtml = "fu7100605.jpg"
            )
        )

        listOf("none", "利用しない", "表示しない").forEach { method ->
            assertFalse(compatApuSmallThumbEnabled(method, wifiConnected = true))
            assertTrue(compatViewerMediaPosts(listOf(post), upsThumbnailMethod = method).isEmpty())
        }
        listOf(null, "", "load", "preload", "wifi", "表示する", "表示する(先読み)").forEach { method ->
            assertTrue(compatApuSmallThumbEnabled(method, wifiConnected = true))
            assertEquals(
                listOf("810b"),
                compatViewerMediaPosts(
                    listOf(post),
                    upsThumbnailMethod = method,
                    wifiConnected = false
                ).map { it.postNo }
            )
        }
    }

    @Test
    fun bareApuSmallVideoIsIncludedAndUsesVideoViewerSource() {
        val normalized = normalizeCompatPostMedia(
            CompatPostSnapshot(
                position = 0,
                postNo = "805",
                timestamp = "",
                messageHtml = "fu7089477.mp4"
            )
        )

        assertEquals("https://dec.2chan.net/up2/src/fu7089477.mp4", normalized.imageUrl)
        assertEquals("https://dec.2chan.net/up2/thumb/fu7089477s.jpg", normalized.thumbnailUrl)
        assertEquals(
            listOf("805"),
            compatViewerMediaPosts(listOf(normalized), upsThumbnailMethod = "読み込む")
                .map { it.postNo }
        )
        assertEquals(normalized.imageUrl, resolveCompatViewerMediaUrl(normalized))
        assertEquals(normalized.thumbnailUrl, resolveCompatPostPreviewUrl(normalized, "読み込む"))
        assertTrue(isCompatVideoMediaUrl(normalized.imageUrl!!))
    }

    @Test
    fun legacyApuSmallImageThumbnailIsReplacedByStableSourceRequest() {
        val normalized = normalizeCompatPostMedia(
            CompatPostSnapshot(
                position = 0,
                postNo = "806",
                timestamp = "",
                messageHtml = "fu1234567.jpg",
                imageUrl = "https://dec.2chan.net/up2/src/fu1234567.jpg",
                thumbnailUrl = "https://dec.2chan.net/up2/thumb/fu1234567s.jpg"
            )
        )

        assertNull(normalized.thumbnailUrl)
        assertEquals(normalized.imageUrl, resolveCompatPostPreviewUrl(normalized, "読み込む"))
    }

    @Test
    fun archiveRewrittenApuMediaIsCanonicalizedAndDeduplicatedByFileName() {
        val archiveUrl = "https://archive.example.test/cache/fu7190971.png"
        val normalized = normalizeCompatPostMedia(
            CompatPostSnapshot(
                position = 0,
                postNo = "812",
                timestamp = "",
                messageHtml = "<a href=\"$archiveUrl\">fu7190971.png</a><span>[見る]</span>",
                imageUrl = archiveUrl,
                thumbnailUrl = archiveUrl
            )
        )

        assertEquals("https://dec.2chan.net/up2/src/fu7190971.png", normalized.imageUrl)
        assertNull(normalized.thumbnailUrl)
        assertFalse(compatPostHasVisibleMedia(normalized, "表示しない", wifiConnected = true))
        assertEquals(
            listOf("https://dec.2chan.net/up2/src/fu7190971.png"),
            compatMediaPostsWithInlineApu(listOf(normalized)).mapNotNull { it.imageUrl }
        )
    }

    @Test
    fun archiveRewrittenApuMediaInQuoteNeverBecomesReplyMedia() {
        val archiveUrl = "https://archive.example.test/cache/fu7190971.png"
        val normalized = normalizeCompatPostMedia(
            CompatPostSnapshot(
                position = 0,
                postNo = "813",
                timestamp = "",
                messageHtml = "&gt;<a href=\"$archiveUrl\">fu7190971.png</a><span>[見る]</span>",
                imageUrl = archiveUrl,
                thumbnailUrl = archiveUrl
            )
        )

        assertNull(normalized.imageUrl)
        assertNull(normalized.thumbnailUrl)
        assertTrue(compatViewerMediaPosts(listOf(normalized)).isEmpty())
    }

    @Test
    fun removedWebmMp4SwitchNeverProbesAnInventedUrl() {
        val webm = "https://may.2chan.net/b/src/123.webm?x=1"
        assertEquals(listOf(webm), compatVideoPlaybackCandidates(webm, switchWebmToMp4 = true))
        assertEquals(listOf(webm), compatVideoPlaybackCandidates(webm, false))
    }
}
