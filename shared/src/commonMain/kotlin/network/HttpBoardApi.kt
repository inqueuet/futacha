package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.util.TextEncoding
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormBuilder
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.text.RegexOption
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// FIX: KMP commonMainでも動くスレッドセーフLRUキャッシュ
// NOTE: LinkedHashMapのaccessOrderコンストラクタ/継承がcommonMainで不可なため、手動で順序管理
private class ThreadSafeLruCache<K, V>(private val maxSize: Int) {
    private val mutex = Mutex()
    private val cache: LinkedHashMap<K, V> = LinkedHashMap()

    // FIX: 読み取り操作もMutexで保護（アクセス順を手動で更新）
    suspend fun get(key: K): V? = mutex.withLock {
        val value = cache.remove(key) ?: return@withLock null
        cache[key] = value
        value
    }

    // FIX: 書き込み操作はMutexで保護（サイズ制限は手動適用）
    suspend fun put(key: K, value: V): V? = mutex.withLock {
        val previous = cache.remove(key)
        cache[key] = value
        if (cache.size > maxSize) {
            val eldestKey = cache.entries.firstOrNull()?.key
            if (eldestKey != null) {
                cache.remove(eldestKey)
            }
        }
        previous
    }

    suspend fun clear() = mutex.withLock {
        cache.clear()
    }

    suspend fun size(): Int = mutex.withLock {
        cache.size
    }
}

class HttpBoardApi(
    private val client: HttpClient
) : BoardApi, AutoCloseable {
    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36"
        private const val DEFAULT_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private const val DEFAULT_ACCEPT_LANGUAGE = "ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7"
        // FIX: OOM防止のため、20MB→5MBに制限を削減
        // 通常のHTMLレスポンスは1-2MB程度なので5MBで十分
        private const val MAX_RESPONSE_SIZE = 5 * 1024 * 1024 // 5MB limit
        private const val DEFAULT_UPLOAD_FILE_NAME = "upload.bin"
        private const val SHIFT_JIS_TEXT_MIME = "text/plain; charset=Shift_JIS"
        private const val UTF8_TEXT_MIME = "text/plain; charset=UTF-8"
        private const val ASCII_TEXT_MIME = "text/plain; charset=US-ASCII"
        private const val DEFAULT_SHIFT_JIS_CHRENC_SAMPLE = "文字"

        // FIX: 投稿入力検証のための定数
        private const val MAX_NAME_LENGTH = 100
        private const val MAX_EMAIL_LENGTH = 100
        private const val MAX_SUBJECT_LENGTH = 100
        private const val MAX_COMMENT_LENGTH = 10000
        private const val MAX_PASSWORD_LENGTH = 100
        private const val MAX_IMAGE_FILE_SIZE = 8_192_000L // 8MB（フォームのMAX_FILE_SIZEと一致）

        /**
         * FIX: 投稿入力検証 - DoS攻撃・インジェクション攻撃防止
         *
         * @throws IllegalArgumentException 入力が不正な場合
         */
        private fun validatePostInput(
            name: String,
            email: String,
            subject: String,
            comment: String,
            password: String,
            imageFile: ByteArray?
        ) {
            // FIX: null文字チェック（全てのテキストフィールド）
            listOf(
                "name" to name,
                "email" to email,
                "subject" to subject,
                "comment" to comment,
                "password" to password
            ).forEach { (fieldName, value) ->
                if (value.contains('\u0000')) {
                    throw IllegalArgumentException("$fieldName contains null character")
                }
            }

            // FIX: 長さ制限チェック
            if (name.length > MAX_NAME_LENGTH) {
                throw IllegalArgumentException("Name exceeds maximum length ($MAX_NAME_LENGTH): ${name.length}")
            }
            if (email.length > MAX_EMAIL_LENGTH) {
                throw IllegalArgumentException("Email exceeds maximum length ($MAX_EMAIL_LENGTH): ${email.length}")
            }
            if (subject.length > MAX_SUBJECT_LENGTH) {
                throw IllegalArgumentException("Subject exceeds maximum length ($MAX_SUBJECT_LENGTH): ${subject.length}")
            }
            if (comment.length > MAX_COMMENT_LENGTH) {
                throw IllegalArgumentException("Comment exceeds maximum length ($MAX_COMMENT_LENGTH): ${comment.length}")
            }
            if (password.length > MAX_PASSWORD_LENGTH) {
                throw IllegalArgumentException("Password exceeds maximum length ($MAX_PASSWORD_LENGTH): ${password.length}")
            }

            // FIX: 画像ファイルサイズチェック
            imageFile?.let { file ->
                if (file.size > MAX_IMAGE_FILE_SIZE) {
                    throw IllegalArgumentException("Image file size (${file.size} bytes) exceeds maximum ($MAX_IMAGE_FILE_SIZE bytes)")
                }
            }
        }

        /**
         * FIX: 削除・投票操作の入力検証
         *
         * @throws IllegalArgumentException 入力が不正な場合
         */
        private fun validateDeletionInput(password: String) {
            // FIX: null文字チェック
            if (password.contains('\u0000')) {
                throw IllegalArgumentException("password contains null character")
            }

            // FIX: 長さ制限チェック
            if (password.length > MAX_PASSWORD_LENGTH) {
                throw IllegalArgumentException("Password exceeds maximum length ($MAX_PASSWORD_LENGTH): ${password.length}")
            }

            // FIX: 空文字列チェック
            if (password.isBlank()) {
                throw IllegalArgumentException("Password must not be blank")
            }
        }

        /**
         * FIX: 削除理由コードの検証
         *
         * @throws IllegalArgumentException 入力が不正な場合
         */
        private fun validateReasonCode(reasonCode: String) {
            // FIX: null文字チェック
            if (reasonCode.contains('\u0000')) {
                throw IllegalArgumentException("reasonCode contains null character")
            }

            // FIX: 長さ制限チェック（理由コードは通常短い）
            if (reasonCode.length > 50) {
                throw IllegalArgumentException("Reason code exceeds maximum length (50): ${reasonCode.length}")
            }
        }
        private const val DEFAULT_SCREEN_SPEC = "1080x1920x24"
        private const val DEFAULT_PTUA_VALUE = "1341647872"
        private val THREAD_ID_REGEX = """res/(\d+)\.html?""".toRegex(RegexOption.IGNORE_CASE)
        private val RES_QUERY_ID_REGEX = """\bres=(\d+)\b""".toRegex(RegexOption.IGNORE_CASE)
        private val SUCCESS_KEYWORDS = listOf("書き込みました", "書き込みました。", "書き込みが完了", "書きこみました")
        private val ERROR_KEYWORDS = listOf("エラー", "error", "荒らし", "規制", "拒否", "連続投稿", "大きすぎ", "時間を置いて")
        private val WEBP_CONTENT_TYPE = ContentType.parse("image/webp")
        private val WEBM_CONTENT_TYPE = ContentType.parse("video/webm")
        private val BMP_CONTENT_TYPE = ContentType.parse("image/bmp")
        private val MP4_CONTENT_TYPE = ContentType.parse("video/mp4")
        private val JSON_STATUS_REGEX = """"status"\s*:\s*"([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
        private val JSON_MESSAGE_REGEX = """"(error|reason|message)"\s*:\s*"([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
        private val JSON_JUMPTO_REGEX = """"jumpto"\s*:\s*(\d+)""".toRegex()
        private val JSON_THISNO_REGEX = """"thisno"\s*:\s*(\d+)""".toRegex()
        // FIX: ReDoS対策 - より具体的なパターンに変更して[^>]*の繰り返しを排除
        // 元: <input[^>]*name="chrenc"[^>]*> は2つの[^>]*が指数的バックトラッキングを引き起こす
        // 修正: nameとvalue属性の順序を限定して確実にマッチさせる
        private val CHRENC_INPUT_REGEX =
            Regex("""<input\s+[^>]{0,200}?name\s*=\s*["']chrenc["'][^>]{0,200}?>""", RegexOption.IGNORE_CASE)
        private val VALUE_ATTR_REGEX =
            Regex("""value\s*=\s*["']([^"']{0,500})["']""", RegexOption.IGNORE_CASE)
        // FIX: PostingConfigキャッシュサイズを削減（100→20）
        // PostingConfigは複雑なオブジェクトなので、メモリ節約のため制限
        // ほとんどのユーザーは数個の板しか使わないため、20で十分
        private const val MAX_CACHE_SIZE = 20
    }

    private suspend fun readResponseBodyAsString(response: HttpResponse): String {
        val channel = response.bodyAsChannel()
        val limit = MAX_RESPONSE_SIZE + 1
        val packet = channel.readRemaining(limit.toLong())
        @Suppress("DEPRECATION")
        val bytes = packet.readBytes()
        if (bytes.size > MAX_RESPONSE_SIZE || !channel.isClosedForRead) {
            channel.cancel(CancellationException("Response size exceeds maximum allowed"))
            throw NetworkException("Response size exceeds maximum allowed ($MAX_RESPONSE_SIZE bytes)")
        }
        return TextEncoding.decodeToString(bytes, response.headers[HttpHeaders.ContentType])
    }

    // FIX: リトライロジック改善（無限ループ防止、一時的障害対策）
    private suspend fun <T> withRetry(
        maxAttempts: Int = 3, // FIX: 2→3に増加（一時的なネットワーク障害対策）
        initialDelayMillis: Long = 500,
        block: suspend () -> T
    ): T {
        var attempt = 0
        var delayMillis = initialDelayMillis
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                // FIX: キャンセル例外は即座に再スロー（リトライしない）
                throw e
            } catch (e: Exception) {
                attempt += 1
                if (attempt >= maxAttempts || !shouldRetry(e)) {
                    throw e
                }
                // FIX: 指数バックオフ（500ms→1s→2s→4s→5s上限）
                delay(delayMillis)
                delayMillis = (delayMillis * 2).coerceAtMost(5_000)
            }
        }
    }

    private fun shouldRetry(error: Exception): Boolean {
        val networkError = error as? NetworkException ?: return false
        val status = networkError.statusCode ?: return false
        return status == 429 || status >= 500
    }

    // FIX: スレッドセーフなLRUキャッシュに変更
    private val postingConfigCache = ThreadSafeLruCache<String, PostingConfig>(MAX_CACHE_SIZE)

    override suspend fun fetchCatalogSetup(board: String) {
        val boardBase = BoardUrlResolver.resolveBoardBaseUrl(board)
        val url = buildString {
            append(boardBase)
            if (!boardBase.endsWith("/")) append('/')
            append("futaba.php?mode=catset")
        }
        try {
            withRetry {
                val response: HttpResponse = client.submitForm(
                    url = url,
                    formParameters = Parameters.build {
                        append("mode", "catset")
                        append("cx", "5")   // カタログの横サイズ
                        append("cy", "60")  // カタログの縦サイズ
                        append("cl", "4")   // 文字数
                        append("cm", "0")   // 文字位置 (0=下, 1=右)
                        append("ci", "0")   // 画像サイズ (0=小)
                        append("vh", "on")  // 見たスレッドを見歴に追加
                    }
                ) {
                    headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                    headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                    headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                    headers[HttpHeaders.CacheControl] = "max-age=0"
                    headers[HttpHeaders.Referrer] = url
                }

                if (!response.status.isSuccess()) {
                    val errorMsg = "HTTP error ${response.status.value} when fetching catalog setup from $url"
                    println("HttpBoardApi: $errorMsg")
                    throw NetworkException(errorMsg, response.status.value)
                }

                // Cookies (posttime, cxyl, etc.) are automatically stored by HttpCookies plugin
                println("HttpBoardApi: Catalog setup cookies initialized for board: $board (cx=5, cy=60)")
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorMsg = "Failed to fetch catalog setup from $url: ${e.message}"
            println("HttpBoardApi: $errorMsg")
            throw NetworkException(errorMsg, cause = e)
        }
    }

    override suspend fun fetchCatalog(board: String, mode: CatalogMode): String {
        val url = BoardUrlResolver.resolveCatalogUrl(board, mode)
        return try {
            withRetry {
                val response: HttpResponse = client.get(url) {
                    headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                    headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                    headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                    headers[HttpHeaders.CacheControl] = "no-cache"
                    headers[HttpHeaders.Pragma] = "no-cache"
                    headers[HttpHeaders.Referrer] = board
                }

                if (!response.status.isSuccess()) {
                    val errorMsg = "HTTP error ${response.status.value} when fetching catalog from $url"
                    println("HttpBoardApi: $errorMsg")
                    throw NetworkException(errorMsg, response.status.value)
                }

                // Check content length before reading
                val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (contentLength != null && contentLength > MAX_RESPONSE_SIZE) {
                    throw NetworkException("Response size ($contentLength bytes) exceeds maximum allowed ($MAX_RESPONSE_SIZE bytes)")
                }

                readResponseBodyAsString(response)
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorMsg = "Failed to fetch catalog from $url: ${e.message}"
            println("HttpBoardApi: $errorMsg")
            throw NetworkException(errorMsg, cause = e)
        }
    }

    override suspend fun fetchThreadHead(board: String, threadId: String, maxLines: Int): String {
        require(maxLines > 0) { "maxLines must be positive" }
        val url = BoardUrlResolver.resolveThreadUrl(board, threadId)
        return try {
            withRetry {
                val response: HttpResponse = client.get(url) {
                    headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                    headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                    headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                    headers[HttpHeaders.CacheControl] = "no-cache"
                    headers[HttpHeaders.Pragma] = "no-cache"
                    val refererBase = BoardUrlResolver.resolveBoardBaseUrl(board).let { base ->
                        if (base.endsWith("/")) base else "$base/"
                    }
                    headers[HttpHeaders.Referrer] = refererBase
                }

                if (!response.status.isSuccess()) {
                    val errorMsg = "HTTP error ${response.status.value} when fetching thread head from $url"
                    println("HttpBoardApi: $errorMsg")
                    throw NetworkException(errorMsg, response.status.value)
                }

                val body = readResponseBodyAsString(response)
                body.lineSequence()
                    .take(maxLines)
                    .joinToString("\n")
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorMsg = "Failed to fetch thread head from $url: ${e.message}"
            println("HttpBoardApi: $errorMsg")
            throw NetworkException(errorMsg, cause = e)
        }
    }

    override suspend fun fetchThread(board: String, threadId: String): String {
        val url = BoardUrlResolver.resolveThreadUrl(board, threadId)
        return try {
            withRetry {
                val response: HttpResponse = client.get(url) {
                    headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                    headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                    headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                    headers[HttpHeaders.CacheControl] = "no-cache"
                    headers[HttpHeaders.Pragma] = "no-cache"
                    val refererBase = BoardUrlResolver.resolveBoardBaseUrl(board).let { base ->
                        if (base.endsWith("/")) base else "$base/"
                    }
                    headers[HttpHeaders.Referrer] = refererBase
                }

                if (!response.status.isSuccess()) {
                    val errorMsg = "HTTP error ${response.status.value} when fetching thread from $url"
                    println("HttpBoardApi: $errorMsg")
                    throw NetworkException(errorMsg, response.status.value)
                }

                // Check content length before reading
                val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (contentLength != null && contentLength > MAX_RESPONSE_SIZE) {
                    throw NetworkException("Response size ($contentLength bytes) exceeds maximum allowed ($MAX_RESPONSE_SIZE bytes)")
                }

                readResponseBodyAsString(response)
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorMsg = "Failed to fetch thread from $url: ${e.message}"
            println("HttpBoardApi: $errorMsg")
            throw NetworkException(errorMsg, cause = e)
        }
    }

    override suspend fun fetchThreadByUrl(threadUrl: String): String {
        val url = threadUrl
        return try {
            withRetry {
                val response: HttpResponse = client.get(url) {
                    headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                    headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                    headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                    headers[HttpHeaders.CacheControl] = "no-cache"
                    headers[HttpHeaders.Pragma] = "no-cache"
                    resolveRefererBaseFromThreadUrl(url)?.let { referer ->
                        headers[HttpHeaders.Referrer] = referer
                    }
                }

                if (!response.status.isSuccess()) {
                    val errorMsg = "HTTP error ${response.status.value} when fetching thread from $url"
                    println("HttpBoardApi: $errorMsg")
                    throw NetworkException(errorMsg, response.status.value)
                }

                val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (contentLength != null && contentLength > MAX_RESPONSE_SIZE) {
                    throw NetworkException("Response size ($contentLength bytes) exceeds maximum allowed ($MAX_RESPONSE_SIZE bytes)")
                }

                readResponseBodyAsString(response)
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorMsg = "Failed to fetch thread from $url: ${e.message}"
            println("HttpBoardApi: $errorMsg")
            throw NetworkException(errorMsg, cause = e)
        }
    }

    override suspend fun voteSaidane(board: String, threadId: String, postId: String) {
        val sanitizedPostId = BoardUrlResolver.sanitizePostId(postId)
        if (sanitizedPostId.isBlank()) {
            throw IllegalArgumentException("Invalid post ID for saidane vote")
        }
        val boardSlug = BoardUrlResolver.resolveBoardSlug(board)
        val siteRoot = BoardUrlResolver.resolveSiteRoot(board)
        val url = "$siteRoot/sd.php?$boardSlug.$sanitizedPostId"
        val referer = BoardUrlResolver.resolveThreadUrl(board, threadId)
        try {
            val response: HttpResponse = client.get(url) {
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = "*/*"
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                headers[HttpHeaders.Referrer] = referer
            }
            if (!response.status.isSuccess()) {
                throw NetworkException("そうだね投票に失敗しました (HTTP ${response.status.value})")
            }
            val result = readResponseBodyAsString(response).trim()
            if (result != "1") {
                throw NetworkException("そうだね投票に失敗しました (応答: '$result')")
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException("Failed to vote saidane: ${e.message}", cause = e)
        }
    }

    private fun resolveRefererBaseFromThreadUrl(threadUrl: String): String? {
        return runCatching {
            val parsed = Url(threadUrl)
            val segments = parsed.encodedPath.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty()) return null
            val baseSegments = segments.dropLast(1)
            val path = if (baseSegments.isEmpty()) "" else "/" + baseSegments.joinToString("/")
            buildString {
                append(parsed.protocol.name)
                append("://")
                append(parsed.host)
                if (parsed.port != parsed.protocol.defaultPort) {
                    append(":${parsed.port}")
                }
                append(path.trimEnd('/'))
                append("/")
            }
        }.getOrNull()
    }

    override suspend fun requestDeletion(board: String, threadId: String, postId: String, reasonCode: String) {
        // FIX: 入力検証を最初に実行
        validateReasonCode(reasonCode)
        val sanitizedPostId = BoardUrlResolver.sanitizePostId(postId)
        if (sanitizedPostId.isBlank()) {
            throw IllegalArgumentException("Invalid post ID for del request")
        }
        val boardSlug = BoardUrlResolver.resolveBoardSlug(board)
        val siteRoot = BoardUrlResolver.resolveSiteRoot(board)
        val referer = BoardUrlResolver.resolveThreadUrl(board, threadId)
        val response = try {
            client.submitForm(
                url = "$siteRoot/del.php",
                formParameters = Parameters.build {
                    append("mode", "post")
                    append("b", boardSlug)
                    append("d", sanitizedPostId)
                    append("reason", reasonCode)
                    append("responsemode", "ajax")
                }
            ) {
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                headers[HttpHeaders.Referrer] = referer
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException("Failed to send del request: ${e.message}", cause = e)
        }
        if (!response.status.isSuccess()) {
            throw NetworkException("del依頼に失敗しました (HTTP ${response.status.value})")
        }
    }

    override suspend fun deleteByUser(
        board: String,
        threadId: String,
        postId: String,
        password: String,
        imageOnly: Boolean
    ) {
        // FIX: 入力検証を最初に実行（既存のチェックを統合）
        validateDeletionInput(password)
        val sanitizedPostId = BoardUrlResolver.sanitizePostId(postId)
        if (sanitizedPostId.isBlank()) {
            throw IllegalArgumentException("Invalid post ID for user deletion")
        }
        val boardBase = BoardUrlResolver.resolveBoardBaseUrl(board)
        val referer = BoardUrlResolver.resolveThreadUrl(board, threadId)
        val url = buildString {
            append(boardBase)
            if (!boardBase.endsWith("/")) append('/')
            append("futaba.php?guid=on")
        }
        val response = try {
            client.submitForm(
                url = url,
                formParameters = Parameters.build {
                    append("guid", "on")
                    append(sanitizedPostId, "delete")
                    append("responsemode", "ajax")
                    append("pwd", password)
                    append("onlyimgdel", if (imageOnly) "on" else "")
                    append("mode", "usrdel")
                }
            ) {
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                headers[HttpHeaders.Referrer] = referer
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException("Failed to delete post: ${e.message}", cause = e)
        }
        if (!response.status.isSuccess()) {
            throw NetworkException("本人削除に失敗しました (HTTP ${response.status.value})")
        }
    }

    override suspend fun createThread(
        board: String,
        name: String,
        email: String,
        subject: String,
        comment: String,
        password: String,
        imageFile: ByteArray?,
        imageFileName: String?,
        textOnly: Boolean
    ): String? {
        // FIX: 入力検証を最初に実行
        validatePostInput(name, email, subject, comment, password, imageFile)
        val boardBase = BoardUrlResolver.resolveBoardBaseUrl(board)
        val referer = buildString {
            append(boardBase)
            if (!boardBase.endsWith("/")) append('/')
            append("futaba.htm")
        }
        val url = buildString {
            append(boardBase)
            if (!boardBase.endsWith("/")) append('/')
            append("futaba.php?guid=on")
        }
        val postingConfig = getPostingConfig(board)
        val formData = buildPostFormData(
            threadId = null,
            name = name,
            email = email,
            subject = subject,
            comment = comment,
            password = password,
            imageFile = imageFile,
            imageFileName = imageFileName,
            textOnly = textOnly,
            postingConfig = postingConfig,
            forceAjaxResponse = true
        )
        val response = try {
            client.submitFormWithBinaryData(
                url = url,
                formData = formData
            ) {
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                headers[HttpHeaders.Referrer] = referer
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException("Failed to create thread: ${e.message}", cause = e)
        }
        if (!response.status.isSuccess()) {
            throw NetworkException("スレッド作成に失敗しました (HTTP ${response.status.value})")
        }

        // Parse response to extract thread ID
        val responseBody = readResponseBodyAsString(response)
        val extractedThreadId = tryExtractThreadId(responseBody)
        if (!extractedThreadId.isNullOrBlank()) {
            return extractedThreadId
        }
        val jsonThreadId = tryParseThreadIdFromJson(responseBody)
        if (jsonThreadId != null) {
            return jsonThreadId
        }
        val errorDetail = extractServerError(responseBody)
        val summary = summarizeResponse(responseBody)
        if (errorDetail != null) {
            throw NetworkException("スレッド作成に失敗しました: $errorDetail")
        }
        if (isSuccessfulPostResponse(responseBody)) {
            com.valoser.futacha.shared.util.Logger.w("HttpBoardApi", "Thread created but thread ID was not found in response")
            return null
        }
        throw NetworkException("スレッドIDの取得に失敗しました: $summary")
    }

    override suspend fun replyToThread(
        board: String,
        threadId: String,
        name: String,
        email: String,
        subject: String,
        comment: String,
        password: String,
        imageFile: ByteArray?,
        imageFileName: String?,
        textOnly: Boolean
    ): String? {
        // FIX: 入力検証を最初に実行
        validatePostInput(name, email, subject, comment, password, imageFile)
        val boardBase = BoardUrlResolver.resolveBoardBaseUrl(board)
        val referer = BoardUrlResolver.resolveThreadUrl(board, threadId)
        val url = buildString {
            append(boardBase)
            if (!boardBase.endsWith("/")) append('/')
            append("futaba.php?guid=on")
        }
        val postingConfig = getPostingConfig(board)
        val formData = buildPostFormData(
            threadId = threadId,
            name = name,
            email = email,
            subject = subject,
            comment = comment,
            password = password,
            imageFile = imageFile,
            imageFileName = imageFileName,
            textOnly = textOnly,
            postingConfig = postingConfig
        )
        val response = try {
            client.submitFormWithBinaryData(
                url = url,
                formData = formData
            ) {
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                headers[HttpHeaders.Referrer] = referer
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException("Failed to reply to thread: ${e.message}", cause = e)
        }
        if (!response.status.isSuccess()) {
            throw NetworkException("返信に失敗しました (HTTP ${response.status.value})")
        }
        val responseBody = readResponseBodyAsString(response)
        if (!isSuccessfulPostResponse(responseBody)) {
            val errorDetail = extractServerError(responseBody)
            val summary = summarizeResponse(responseBody)
            val detail = errorDetail ?: summary
            throw NetworkException("返信に失敗しました: $detail")
        }
        return tryExtractThisNo(responseBody)
    }

    // SECURITY NOTE: パスワードはStringで渡されるため、メモリダンプから漏洩する可能性があります。
    // 将来的な改善案:
    // 1. CharArrayを使用してパスワードを渡し、使用後即座にクリア
    // 2. プラットフォーム固有のSecureString実装を使用
    // 3. パスワードの保持時間を最小化
    // 現状では、呼び出し元がパスワードの寿命管理を行う必要があります。
    private fun buildPostFormData(
        threadId: String?,
        name: String,
        email: String,
        subject: String,
        comment: String,
        password: String,
        imageFile: ByteArray?,
        imageFileName: String?,
        textOnly: Boolean,
        postingConfig: PostingConfig,
        forceAjaxResponse: Boolean = false
    ) = formData {
        appendAsciiField("guid", "on")
        appendAsciiField("mode", "regist")
        appendAsciiField("MAX_FILE_SIZE", "8192000")
        appendTextField("name", name, postingConfig.encoding)
        appendTextField("email", email, postingConfig.encoding)
        appendTextField("sub", subject, postingConfig.encoding)
        appendTextField("com", comment, postingConfig.encoding)
        appendTextField("pwd", password, postingConfig.encoding)
        appendTextField("chrenc", postingConfig.chrencValue, postingConfig.encoding)
        appendAsciiField("js", "on")
        appendAsciiField("baseform", "")
        appendAsciiField("pthb", "")
        appendAsciiField("pthc", generateClientTimestampSeed())
        appendAsciiField("pthd", "")
        appendAsciiField("ptua", DEFAULT_PTUA_VALUE)
        appendAsciiField("scsz", DEFAULT_SCREEN_SPEC)
        appendAsciiField("hash", generateClientHash())
        threadId?.let {
            appendAsciiField("resto", it)
            appendAsciiField("responsemode", "ajax")
        } ?: run {
            if (forceAjaxResponse) {
                appendAsciiField("responsemode", "ajax")
            }
        }

        val attachImage = shouldAttachImage(imageFile, textOnly)
        if (attachImage) {
            val safeName = sanitizeFileName(imageFileName)
            // FIX: requireNotNullの代わりにエルビス演算子で安全にフォールバック
            // shouldAttachImageがtrueならimageFileはnullでないはずだが、
            // 防御的プログラミングとして空配列にフォールバックする
            val fileData = imageFile ?: ByteArray(0)
            if (fileData.isEmpty()) {
                com.valoser.futacha.shared.util.Logger.w("HttpBoardApi", "imageFile is unexpectedly null or empty when attachImage is true")
            }
            append(
                "upfile",
                fileData,
                Headers.build {
                    append(
                        HttpHeaders.ContentDisposition,
                        """form-data; name="upfile"; filename="$safeName""""
                    )
                    append(HttpHeaders.ContentType, guessMediaContentType(safeName).toString())
                }
            )
        } else {
            append("textonly", "on")
            append(
                "upfile",
                ByteArray(0),
                Headers.build {
                    append(
                        HttpHeaders.ContentDisposition,
                        """form-data; name="upfile"; filename="""
                    )
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                }
            )
        }
    }

    private fun shouldAttachImage(imageFile: ByteArray?, textOnly: Boolean): Boolean {
        return !textOnly && imageFile != null && imageFile.isNotEmpty()
    }

    private fun sanitizeFileName(original: String?): String {
        if (original.isNullOrBlank()) return DEFAULT_UPLOAD_FILE_NAME
        val trimmed = original.trim()
        val sanitized = buildString(trimmed.length) {
            for (ch in trimmed) {
                when {
                    ch.isLetterOrDigit() -> append(ch)
                    ch == '.' || ch == '-' || ch == '_' -> append(ch)
                    ch.isWhitespace() -> append('_')
                    else -> append('_')
                }
            }
        }
        return sanitized.ifBlank { DEFAULT_UPLOAD_FILE_NAME }
    }

    private fun guessMediaContentType(fileName: String): ContentType {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg", "jpe" -> ContentType.Image.JPEG
            "png" -> ContentType.Image.PNG
            "gif" -> ContentType.Image.GIF
            "bmp" -> BMP_CONTENT_TYPE
            "webp" -> WEBP_CONTENT_TYPE
            "webm" -> WEBM_CONTENT_TYPE
            "mp4" -> MP4_CONTENT_TYPE
            else -> ContentType.Application.OctetStream
        }
    }

    private fun isSuccessfulPostResponse(body: String): Boolean {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return false
        if (looksLikeJson(trimmed) && isJsonStatusOk(trimmed)) {
            return true
        }
        if (containsThreadId(trimmed)) {
            return true
        }
        return SUCCESS_KEYWORDS.any { keyword -> trimmed.contains(keyword) }
    }

    private fun extractServerError(body: String): String? {
        val normalized = body.replace("\r\n", "\n")
        if (looksLikeJson(normalized)) {
            if (isJsonStatusOk(normalized)) {
                return null
            }
            val message = JSON_MESSAGE_REGEX.find(normalized)?.groupValues?.getOrNull(2)
            if (message != null) {
                return message
            }
            val status = JSON_STATUS_REGEX.find(normalized)?.groupValues?.getOrNull(1)
            return status?.let { "status=$it" }
        }
        return normalized.lineSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                line.isNotEmpty() && ERROR_KEYWORDS.any { keyword ->
                    line.contains(keyword, ignoreCase = true)
                }
            }
    }

    private fun summarizeResponse(body: String): String {
        val normalized = body.replace("\r\n", "\n")
        if (looksLikeJson(normalized)) {
            val status = JSON_STATUS_REGEX.find(normalized)?.groupValues?.getOrNull(1)
            val message = JSON_MESSAGE_REGEX.find(normalized)?.groupValues?.getOrNull(2)
            return buildString {
                append("status=${status ?: "unknown"}")
                if (!message.isNullOrBlank()) {
                    append(", message=")
                    append(message)
                }
            }
        }
        return normalized.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.take(120)
            ?: body.take(120)
    }

    private fun tryParseThreadIdFromJson(body: String): String? {
        if (!looksLikeJson(body) || !isJsonStatusOk(body)) {
            return null
        }
        val jumpto = JSON_JUMPTO_REGEX.find(body)?.groupValues?.getOrNull(1)
        if (!jumpto.isNullOrBlank()) {
            return jumpto
        }
        val thisNo = JSON_THISNO_REGEX.find(body)?.groupValues?.getOrNull(1)
        if (!thisNo.isNullOrBlank()) {
            return thisNo
        }
        return null
    }

    private fun tryExtractThreadId(body: String): String? {
        THREAD_ID_REGEX.find(body)?.groupValues?.getOrNull(1)?.let { return it }
        RES_QUERY_ID_REGEX.find(body)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }

    private fun containsThreadId(body: String): Boolean {
        return THREAD_ID_REGEX.containsMatchIn(body) || RES_QUERY_ID_REGEX.containsMatchIn(body)
    }

    private fun tryExtractThisNo(body: String): String? {
        val match = JSON_THISNO_REGEX.find(body)
        return match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun looksLikeJson(body: String): Boolean {
        val firstNonWhitespace = body.firstOrNull { !it.isWhitespace() }
        return firstNonWhitespace == '{' || firstNonWhitespace == '['
    }

    private fun isJsonStatusOk(body: String): Boolean {
        val status = JSON_STATUS_REGEX.find(body)?.groupValues?.getOrNull(1)?.lowercase()
        return status == "ok" || status == "success"
    }

    private fun FormBuilder.appendTextField(name: String, value: String, encoding: PostEncoding) {
        val (bytes, contentType) = when (encoding) {
            PostEncoding.SHIFT_JIS -> TextEncoding.encodeToShiftJis(value) to SHIFT_JIS_TEXT_MIME
            PostEncoding.UTF8 -> value.encodeToByteArray() to UTF8_TEXT_MIME
        }
        append(
            name,
            bytes,
            Headers.build {
                append(HttpHeaders.ContentDisposition, """form-data; name="$name"""")
                append(HttpHeaders.ContentType, contentType)
            }
        )
    }

    private fun FormBuilder.appendAsciiField(name: String, value: String) {
        append(
            name,
            value,
            Headers.build {
                append(HttpHeaders.ContentDisposition, """form-data; name="$name"""")
                append(HttpHeaders.ContentType, ASCII_TEXT_MIME)
            }
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun generateClientHash(): String {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val randomHex = buildString(32) {
            repeat(16) {
                val value = Random.nextInt(0, 256)
                append(value.toString(16).padStart(2, '0'))
            }
        }
        return "$timestamp-$randomHex"
    }

    @OptIn(ExperimentalTime::class)
    private fun generateClientTimestampSeed(): String =
        Clock.System.now().toEpochMilliseconds().toString()

    private suspend fun getPostingConfig(board: String): PostingConfig {
        // FIX: キャッシュ自体がスレッドセーフなのでMutexは不要
        postingConfigCache.get(board)?.let { return it }

        val fetched = fetchPostingConfig(board)
        postingConfigCache.put(board, fetched)
        return fetched
    }

    private suspend fun fetchPostingConfig(board: String): PostingConfig {
        return try {
            val boardBase = BoardUrlResolver.resolveBoardBaseUrl(board)
            val url = buildString {
                append(boardBase)
                if (!boardBase.endsWith("/")) append('/')
                append("futaba.htm")
            }
            val response = client.get(url) {
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
            }
            if (!response.status.isSuccess()) {
                throw NetworkException("HTTP error ${response.status.value} when fetching posting config from $url")
            }
            val html = readResponseBodyAsString(response)
            val chrencValue = parseChrencValue(html) ?: DEFAULT_SHIFT_JIS_CHRENC_SAMPLE
            PostingConfig(
                encoding = determineEncoding(chrencValue),
                chrencValue = chrencValue
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            PostingConfig(
                encoding = PostEncoding.SHIFT_JIS,
                chrencValue = DEFAULT_SHIFT_JIS_CHRENC_SAMPLE
            )
        }
    }

    private fun parseChrencValue(html: String): String? {
        // FIX: ReDoS対策 - 入力サイズ制限（最大100KB）
        if (html.length > 100_000) {
            return null
        }
        val input = CHRENC_INPUT_REGEX.find(html)?.value ?: return null
        val match = VALUE_ATTR_REGEX.find(input) ?: return null
        val rawValue = match.groupValues.getOrNull(1)?.trim().orEmpty()
        if (rawValue.isEmpty()) return null
        return decodeNumericEntities(rawValue)
    }

    private fun decodeNumericEntities(value: String): String {
        if (!value.contains("&#")) return value
        val numericEntityRegex = Regex("""&#(x?[0-9a-fA-F]+);""")
        return numericEntityRegex.replace(value) { match ->
            val payload = match.groupValues.getOrNull(1) ?: return@replace match.value
            val codePoint = when {
                payload.startsWith("x") || payload.startsWith("X") -> payload.drop(1).toIntOrNull(16)
                else -> payload.toIntOrNull(10)
            }
            codePoint?.let {
                // FIX: サロゲートペア計算の修正 - 範囲チェックと正しい変換
                when {
                    it in 0x0000..0xFFFF -> {
                        // BMP (Basic Multilingual Plane) - 直接変換可能
                        if (it in 0xD800..0xDFFF) {
                            // サロゲート範囲は無効
                            return@replace match.value
                        }
                        it.toChar().toString()
                    }
                    it in 0x10000..0x10FFFF -> {
                        // 補助平面 - サロゲートペアが必要
                        val adjusted = it - 0x10000
                        val highSurrogate = 0xD800 + (adjusted shr 10)
                        val lowSurrogate = 0xDC00 + (adjusted and 0x3FF)
                        buildString {
                            append(highSurrogate.toChar())
                            append(lowSurrogate.toChar())
                        }
                    }
                    else -> {
                        // 範囲外のコードポイントは無視
                        match.value
                    }
                }
            } ?: match.value
        }
    }

    private fun determineEncoding(chrencValue: String): PostEncoding {
        val normalized = chrencValue.lowercase()
        return if (normalized.contains("unicode") || normalized.contains("utf")) {
            PostEncoding.UTF8
        } else {
            PostEncoding.SHIFT_JIS
        }
    }

    private data class PostingConfig(
        val encoding: PostEncoding,
        val chrencValue: String
    )

    private enum class PostEncoding {
        SHIFT_JIS,
        UTF8
    }

    override fun close() {
        client.close()
    }
}
