package com.valoser.futacha.shared.network

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.ContentType
import io.ktor.http.content.PartData
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpBoardApiSupportTest {
    @Test
    fun postResponseHelpers_extractThreadIds_fromHtmlAndJson() {
        assertEquals(
            "123456",
            tryExtractHttpBoardApiThreadId("""<a href="res/123456.htm">thread</a>""")
        )
        assertEquals(
            "654321",
            tryExtractHttpBoardApiThreadId("""{"redirect":"futaba.php?res=654321"}""")
        )
        assertEquals(
            "777",
            tryParseHttpBoardApiThreadIdFromJson("""{"status":"ok","jumpto":777}""")
        )
        assertEquals(
            "888",
            tryParseHttpBoardApiThreadIdFromJson("""{"status":"success","thisno":888}""")
        )
        assertNull(tryParseHttpBoardApiThreadIdFromJson("""{"status":"error","message":"nope"}"""))
    }

    @Test
    fun postResponseHelpers_detectSuccessAndErrors() {
        assertTrue(isSuccessfulHttpBoardApiPostResponse("書き込みました。"))
        assertTrue(isSuccessfulHttpBoardApiPostResponse("""{"status":"ok","thisno":10}"""))
        assertTrue(isSuccessfulHttpBoardApiPostResponse("""<meta http-equiv="refresh" content="0;URL=res/10.htm">"""))
        assertFalse(isSuccessfulHttpBoardApiPostResponse("""{"status":"error","message":"規制中"}"""))
        assertTrue(isSuccessfulHttpBoardApiSaidaneResponse("1"))
        assertTrue(isSuccessfulHttpBoardApiSaidaneResponse("42"))
        assertTrue(isSuccessfulHttpBoardApiSaidaneResponse("""{"status":"ok"}"""))
        assertFalse(isSuccessfulHttpBoardApiSaidaneResponse("error"))

        assertEquals(
            "規制中",
            extractHttpBoardApiServerError("""{"status":"error","message":"規制中"}""")
        )
        assertEquals(
            "status=error",
            extractHttpBoardApiServerError("""{"status":"error"}""")
        )
        assertEquals(
            "エラー: 時間を置いてください",
            extractHttpBoardApiServerError("\n\nエラー: 時間を置いてください\n")
        )
        assertEquals(
            "IP制限により あと3500秒書き込みができません",
            extractHttpBoardApiServerError(
                """<html><body><b>IP制限</b>により<br>あと3500秒書き込みができません</body></html>"""
            )
        )
        assertEquals(
            "あなたのIPからは投稿できません あと3500秒投稿できません",
            extractHttpBoardApiServerError(
                """
                    <html><body>
                    <div>あなたのIPからは投稿できません</div>
                    <div>あと3500秒投稿できません</div>
                    </body></html>
                """.trimIndent()
            )
        )
        assertEquals(
            "Cookieを新規作成しました。もう一度書き込みしてください",
            extractHttpBoardApiServerError(
                """<html><body>Cookieを新規作成しました。もう一度書き込みしてください</body></html>"""
            )
        )
        assertNull(extractHttpBoardApiServerError("""{"status":"ok","thisno":10}"""))
    }

    @Test
    fun postResponseHelpers_detectCookieResetRecoveryRequirement() {
        assertTrue(
            requiresHttpBoardApiCookieResetRecovery("posttime の期限切れです")
        )
        assertTrue(
            requiresHttpBoardApiCookieResetRecovery("Cookie を削除して書き込み可能なIPから再生成してください")
        )
        assertTrue(
            requiresHttpBoardApiCookieResetRecovery("cookieを有効にしてもう一度送信してください")
        )
        assertFalse(
            requiresHttpBoardApiCookieResetRecovery("規制中です")
        )
        assertEquals(
            "返信に失敗しました: posttime の期限切れです 今回の投稿試行で投稿用 Cookie が保存された可能性があります。Cookie を保持したままもう一度投稿してください。残り秒数が表示された場合は、その時間まで待ってください",
            buildHttpBoardApiPostingFailureMessage(
                prefix = "返信に失敗しました",
                detail = "posttime の期限切れです"
            )
        )
        assertEquals(
            "返信に失敗しました: cookieを有効にしてもう一度送信してください 今回の投稿試行で投稿用 Cookie が保存された可能性があります。Cookie を保持したままもう一度投稿してください。残り秒数が表示された場合は、その時間まで待ってください",
            buildHttpBoardApiPostingFailureMessage(
                prefix = "返信に失敗しました",
                detail = "cookieを有効にしてもう一度送信してください"
            )
        )
    }

    @Test
    fun postResponseHelpers_extractWaitSecondsAndAppendRetryLabel() {
        assertEquals(45L, extractHttpBoardApiPostingWaitSeconds("あと45秒待ってください"))
        assertEquals(200L, extractHttpBoardApiPostingWaitSeconds("3分20秒後に再試行してください"))
        assertEquals(3500L, extractHttpBoardApiPostingWaitSeconds("IP制限によりあと3500秒書き込みができません"))
        assertEquals(86_400L, extractHttpBoardApiPostingWaitSeconds("Cookie 作成後 1日ほど書き込みできません"))
        assertEquals("1分15秒", formatHttpBoardApiPostingWaitLabel(75L))
        assertEquals("1日1時間", formatHttpBoardApiPostingWaitLabel(90_000L))

        assertEquals(
            "返信に失敗しました: あと45秒待ってください 約45秒後に再試行してください",
            buildHttpBoardApiPostingFailureMessage(
                prefix = "返信に失敗しました",
                detail = "あと45秒待ってください"
            )
        )
        assertEquals(
            "返信に失敗しました: posttime の期限切れです あと86400秒待ってください 今回の投稿試行で投稿用 Cookie が保存された可能性があります。Cookie を保持したまま約1日後に再試行してください",
            buildHttpBoardApiPostingFailureMessage(
                prefix = "返信に失敗しました",
                detail = "posttime の期限切れです あと86400秒待ってください"
            )
        )
        assertEquals(
            "返信に失敗しました: IP制限によりあと3500秒書き込みができません 約58分20秒後に再試行してください",
            buildHttpBoardApiPostingFailureMessage(
                prefix = "返信に失敗しました",
                detail = "IP制限によりあと3500秒書き込みができません"
            )
        )
    }

    @Test
    fun postResponseHelpers_classifyTemporaryAndIpRestrictions() {
        assertEquals(
            HttpBoardApiPostingFailureKind.TEMPORARY_RESTRICTION,
            classifyHttpBoardApiPostingFailure("連続投稿です。少し待ってください")
        )
        assertEquals(
            HttpBoardApiPostingFailureKind.IP_RESTRICTION,
            classifyHttpBoardApiPostingFailure("規制中です。この回線からは書き込めません")
        )
        assertEquals(
            HttpBoardApiPostingFailureKind.COOKIE_RESET_REQUIRED,
            classifyHttpBoardApiPostingFailure("Cookieを新規作成しました。もう一度書き込みしてください")
        )
        assertEquals(
            HttpBoardApiPostingFailureKind.COOKIE_RESET_REQUIRED,
            classifyHttpBoardApiPostingFailure("cookieを有効にしてもう一度送信してください")
        )
        assertEquals(
            HttpBoardApiPostingFailureKind.IP_RESTRICTION,
            classifyHttpBoardApiPostingFailure("IP制限によりあと3500秒書き込みができません")
        )
        assertEquals(
            "返信に失敗しました: 規制中です。この回線からは書き込めません IP規制の可能性があります。時間を置くか、別の書き込み可能な回線を試してください",
            buildHttpBoardApiPostingFailureMessage(
                prefix = "返信に失敗しました",
                detail = "規制中です。この回線からは書き込めません"
            )
        )
        assertEquals(
            "返信に失敗しました: 連続投稿です。少し待ってください",
            buildHttpBoardApiPostingFailureMessage(
                prefix = "返信に失敗しました",
                detail = "連続投稿です。少し待ってください"
            )
        )
    }

    @Test
    fun postResponseHelpers_summarizeJsonAndPlainText() {
        assertEquals(
            "status=error, message=規制中",
            summarizeHttpBoardApiResponse("""{"status":"error","message":"規制中"}""")
        )
        assertEquals(
            "最初の行",
            summarizeHttpBoardApiResponse("\n 最初の行 \n 次の行")
        )
    }

    @Test
    fun postResponseHelpers_extractThisNo_and_jsonShape() {
        assertEquals("123", tryExtractHttpBoardApiThisNo("""{"status":"ok","thisno":123}"""))
        assertNull(tryExtractHttpBoardApiThisNo("""{"status":"ok"}"""))
        assertTrue(looksLikeHttpBoardApiJson(" {\"status\":\"ok\"}"))
        assertFalse(looksLikeHttpBoardApiJson("<html></html>"))
        assertTrue(isHttpBoardApiJsonStatusOk("""{"status":"success"}"""))
        assertFalse(isHttpBoardApiJsonStatusOk("""{"status":"error"}"""))
    }

    @Test
    fun chrencHelpers_extractAndDecodeEntities() {
        val html = """
            <html>
              <form>
                <input type="hidden" name="chrenc" value="&#x6587;&#23383;">
              </form>
            </html>
        """.trimIndent()

        assertEquals("文字", parseHttpBoardApiChrencValue(html))
        assertEquals(
            "abc-123",
            parseHttpBoardApiInputValue("""<input type=hidden name=hash value="abc-123">""", "hash")
        )
        assertEquals(
            "UTF-8",
            parseHttpBoardApiInputValue("""<input value='UTF-8' name='chrenc' type='hidden'>""", "chrenc")
        )
        assertEquals("A", decodeHttpBoardApiNumericEntities("&#65;"))
        assertEquals("\uD83D\uDE00", decodeHttpBoardApiNumericEntities("&#x1F600;"))
        assertEquals("plain", decodeHttpBoardApiNumericEntities("plain"))
    }

    @Test
    fun runtimeHelpers_handleRetryRefererFileNameAndContentType() {
        assertTrue(shouldRetryHttpBoardApiRequest(HttpRequestTimeoutException("timeout", null)))
        assertFalse(shouldRetryHttpBoardApiRequest(Exception(CancellationException("cancelled"))))

        assertEquals(
            "https://may.2chan.net/b/res/",
            resolveHttpBoardApiRefererBaseFromThreadUrl("https://may.2chan.net/b/res/123.htm")
        )
        assertNull(resolveHttpBoardApiRefererBaseFromThreadUrl("not-a-url"))

        assertTrue(shouldAttachHttpBoardApiImage(byteArrayOf(1), textOnly = false))
        assertFalse(shouldAttachHttpBoardApiImage(byteArrayOf(), textOnly = false))
        assertFalse(shouldAttachHttpBoardApiImage(byteArrayOf(1), textOnly = true))

        assertEquals(
            "a_b_.jpg",
            sanitizeHttpBoardApiUploadFileName(" a b?.jpg ", "upload.bin")
        )
        assertEquals(
            "upload.bin",
            sanitizeHttpBoardApiUploadFileName("   ", "upload.bin")
        )
        assertEquals(
            ContentType.Video.MP4,
            guessHttpBoardApiMediaContentType(
                fileName = "video.mp4",
                webpContentType = ContentType.parse("image/webp"),
                webmContentType = ContentType.parse("video/webm"),
                bmpContentType = ContentType.parse("image/bmp"),
                mp4ContentType = ContentType.parse("video/mp4")
            )
        )
    }

    @Test
    fun runtimeHelpers_lruCache_updatesAccessOrder() = runBlocking {
        val cache = HttpBoardApiThreadSafeLruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)

        assertEquals(1, cache.get("a"))

        cache.put("c", 3)

        assertEquals(1, cache.get("a"))
        assertNull(cache.get("b"))
        assertEquals(3, cache.get("c"))
    }

    @Test
    fun executionHelpers_retryAndPostingConfigCacheBehaveAsExpected() = runBlocking {
        var retryAttempts = 0
        val retryResult = withHttpBoardApiRetry(
            logTag = "HttpBoardApiTest",
            requestAttemptTimeoutMillis = 1_000L,
            maxAttempts = 3,
            initialDelayMillis = 0L
        ) {
            retryAttempts += 1
            if (retryAttempts == 1) {
                throw HttpRequestTimeoutException("timeout", null)
            }
            "ok"
        }
        assertEquals("ok", retryResult)
        assertEquals(2, retryAttempts)

        val cache = HttpBoardApiThreadSafeLruCache<String, HttpBoardApiPostingConfig>(2)
        val locksGuard = Mutex()
        val locks = mutableMapOf<String, HttpBoardApiPostingConfigLockEntry>()
        var fetchCount = 0
        val first = getOrLoadHttpBoardApiPostingConfig(
            board = "https://may.2chan.net/b/",
            cache = cache,
            locksGuard = locksGuard,
            locks = locks,
            fallbackChrencValue = "文字",
            logTag = "HttpBoardApiTest"
        ) {
            fetchCount += 1
            resolveHttpBoardApiPostingConfig(
                chrencValue = "UTF-8",
                fallbackChrencValue = "文字"
            )
        }
        val second = getOrLoadHttpBoardApiPostingConfig(
            board = "https://may.2chan.net/b/",
            cache = cache,
            locksGuard = locksGuard,
            locks = locks,
            fallbackChrencValue = "文字",
            logTag = "HttpBoardApiTest"
        ) {
            fetchCount += 1
            fallbackHttpBoardApiPostingConfig("文字")
        }

        assertEquals(
            HttpBoardApiPostingConfig(
                encoding = HttpBoardApiPostEncoding.UTF8,
                chrencValue = "UTF-8",
                fromFallback = false
            ),
            first
        )
        assertEquals(first, second)
        assertEquals(1, fetchCount)
    }

    @Test
    fun postingHelpers_resolvePostingConfigAndBuildSeeds() {
        assertEquals(
            HttpBoardApiPostingConfig(
                encoding = HttpBoardApiPostEncoding.UTF8,
                chrencValue = "UTF-8",
                fromFallback = false
            ),
            resolveHttpBoardApiPostingConfig(
                chrencValue = "UTF-8",
                fallbackChrencValue = "文字"
            )
        )
        assertEquals(
            HttpBoardApiPostingConfig(
                encoding = HttpBoardApiPostEncoding.SHIFT_JIS,
                chrencValue = "文字",
                cacheable = false,
                fromFallback = true
            ),
            fallbackHttpBoardApiPostingConfig("文字")
        )
        assertEquals("123456", buildHttpBoardApiClientTimestampSeed(currentEpochMillis = 123456L))
        assertEquals(
            "42-000102030405060708090a0b0c0d0e0f",
            buildHttpBoardApiClientHash(
                currentEpochMillis = 42L,
                randomByteSupplier = run {
                    var next = 0
                    { next++ }
                }
            )
        )
    }

    @Test
    fun postingHelpers_buildFormDataUsesServerHashWhenAvailable() {
        val formData = buildHttpBoardApiPostFormData(
            logTag = "HttpBoardApiTest",
            threadId = "777",
            name = "",
            email = "",
            subject = "",
            comment = "reply",
            password = "1234",
            imageFile = null,
            imageFileName = null,
            textOnly = true,
            postingConfig = HttpBoardApiPostingConfig(
                encoding = HttpBoardApiPostEncoding.UTF8,
                chrencValue = "UTF-8",
                hashValue = "server-hash",
                ptuaValue = "server-ptua"
            )
        )

        val hashPart = formData
            .filterIsInstance<PartData.FormItem>()
            .first { it.name == "hash" }
        val ptuaPart = formData
            .filterIsInstance<PartData.FormItem>()
            .first { it.name == "ptua" }

        assertEquals("server-hash", hashPart.value)
        assertEquals("server-ptua", ptuaPart.value)
    }
}
