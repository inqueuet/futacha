package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.media.FutabaMediaKind
import com.valoser.futacha.shared.media.classifyFutabaMedia
import com.valoser.futacha.shared.media.mediaFileExtension
import com.valoser.futacha.shared.network.readBoundedHttpResponseBytes
import com.valoser.futacha.shared.network.readBoundedHttpResponseText
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * The APK exposes ascii2d as a user-configurable image-search endpoint.  The
 * compatibility mode keeps that escape hatch, but only permits the provider
 * host itself so a pasted setting cannot silently turn the image action into
 * an arbitrary POST target.
 */
internal const val DEFAULT_COMPAT_ASCII2D_ENDPOINT =
    "https://ascii2d.net/imagesearch/search"

// The reference APK does not silently start a network search on first use.
// It presents the ascii2d registration dialog once, then remembers that the
// provider has been enabled. Keep the endpoint separate so installations
// created by an earlier compatibility build remain usable.
internal const val COMPAT_ASCII2D_ENABLED_KEY = "compat.common.commonAscii2dSearch"
internal const val COMPAT_ASCII2D_ENDPOINT_KEY = "compat.image_search.ascii2d_url"

internal fun compatAscii2dEndpoint(preferences: Map<String, String>): String =
    preferences[COMPAT_ASCII2D_ENDPOINT_KEY]
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: DEFAULT_COMPAT_ASCII2D_ENDPOINT

internal fun isCompatAscii2dRegistered(preferences: Map<String, String>): Boolean {
    val configured = preferences[COMPAT_ASCII2D_ENDPOINT_KEY]?.trim().orEmpty()
    // A valid endpoint is also treated as an explicit registration. This is
    // the migration path for builds that exposed the custom endpoint before
    // the reference-compatible first-use gate was added.
    return (configured.isNotEmpty() && isValidCompatAscii2dEndpoint(configured)) ||
        (preferences[COMPAT_ASCII2D_ENABLED_KEY] == "ON" &&
            isValidCompatAscii2dEndpoint(compatAscii2dEndpoint(preferences)))
}

internal enum class CompatImageSearchEngine(
    val preferenceName: String,
    val label: String
) {
    TINEYE("TinEye Search", "TinEye Search"),
    IQDB("IQDB Search", "IQDB Search"),
    SAUCENAO("SauceNAO Search", "SauceNAO Search"),
    YANDEX("Yandex画像検索", "Yandex画像検索")
}

internal enum class CompatImageSearchMethod { FILE, URL }

internal sealed interface CompatImageSearchResult {
    val title: String

    data class RemoteUrl(
        override val title: String,
        val url: String
    ) : CompatImageSearchResult

    data class InlineHtml(
        override val title: String,
        val html: String,
        val baseUrl: String
    ) : CompatImageSearchResult
}

/** Exact selectable providers exposed by sample/1.apk (old-ui-18-10b). */
internal enum class CompatImageSearchTarget(
    val id: String,
    val label: String,
    val method: CompatImageSearchMethod,
    val urlTemplate: String? = null
) {
    GOOGLE_FILE("google.file", "Google画像検索 (File)", CompatImageSearchMethod.FILE),
    GOOGLE_URL(
        "google.url", "Google画像検索 (URL)", CompatImageSearchMethod.URL,
        "https://www.google.com/searchbyimage?hl=ja&safe=off&client=chrome&image_url=%s"
    ),
    LENS_FILE("lens.file", "Google Lens (File)", CompatImageSearchMethod.FILE),
    LENS_URL(
        "lens.url", "Google Lens (URL)", CompatImageSearchMethod.URL,
        "https://lens.google.com/uploadbyurl?url=%s"
    ),
    ASCII2D_URL("ascii2d.url", "二次元画像類似検索 (URL)", CompatImageSearchMethod.URL),
    TINEYE_URL(
        "tineye.url", "TinEye Search (URL)", CompatImageSearchMethod.URL,
        "https://tineye.com/search/?url=%s"
    ),
    IQDB_FILE("iqdb.file", "IQDB Search (File)", CompatImageSearchMethod.FILE),
    IQDB_URL("iqdb.url", "IQDB Search (URL)", CompatImageSearchMethod.URL, "https://iqdb.org/?url=%s"),
    SAUCENAO_FILE("saucenao.file", "SauceNAO Search (File)", CompatImageSearchMethod.FILE),
    SAUCENAO_URL(
        "saucenao.url", "SauceNAO Search (URL)", CompatImageSearchMethod.URL,
        "https://saucenao.com/search.php?db=999&url=%s"
    ),
    YANDEX_FILE("yandex.file", "Yandex画像検索 (File)", CompatImageSearchMethod.FILE),
    YANDEX_URL(
        "yandex.url", "Yandex画像検索 (URL)", CompatImageSearchMethod.URL,
        "https://yandex.com/images/search?rpt=imageview&url=%s"
    ),
    BING_URL(
        "bing.url", "Bing Visual Search (URL)", CompatImageSearchMethod.URL,
        "https://www.bing.com/images/search?view=detailv2&iss=sbi&form=SBIVSP&q=imgurl:%s"
    )
}

internal val DEFAULT_COMPAT_IMAGE_SEARCH_TARGETS = setOf(
    CompatImageSearchTarget.LENS_FILE,
    CompatImageSearchTarget.ASCII2D_URL
)

/** Reverse-image search modes exposed from the media action menu. */
internal enum class CompatGoogleImageSearchMode(
    val label: String
) {
    LEGACY("Google画像検索 (URL)"),
    GOOGLE_FILE("Google画像検索 (File)"),
    LENS_URL("Google Lens (URL)"),
    LENS_FILE("Google Lens (File)")
}

internal fun compatGoogleModesForTargets(
    targets: Collection<CompatImageSearchTarget>
): List<CompatGoogleImageSearchMode> = buildList {
    if (CompatImageSearchTarget.GOOGLE_FILE in targets) add(CompatGoogleImageSearchMode.GOOGLE_FILE)
    if (CompatImageSearchTarget.GOOGLE_URL in targets) add(CompatGoogleImageSearchMode.LEGACY)
    if (CompatImageSearchTarget.LENS_FILE in targets) add(CompatGoogleImageSearchMode.LENS_FILE)
    if (CompatImageSearchTarget.LENS_URL in targets) add(CompatGoogleImageSearchMode.LENS_URL)
}

internal const val COMPAT_CUSTOM_IMAGE_SEARCH_KEY = "compat.image_search.engines"

internal fun parseCompatImageSearchTargets(raw: String?): List<CompatImageSearchTarget> {
    if (raw == null) return CompatImageSearchTarget.entries.filter { it in DEFAULT_COMPAT_IMAGE_SEARCH_TARGETS }
    val values = raw.split('|').map(String::trim).filter(String::isNotBlank).toSet()
    val migrated = buildSet {
        addAll(values)
        if ("TinEye Search" in values) add(CompatImageSearchTarget.TINEYE_URL.id)
        if ("IQDB Search" in values) add(CompatImageSearchTarget.IQDB_URL.id)
        if ("SauceNAO Search" in values) add(CompatImageSearchTarget.SAUCENAO_URL.id)
        if ("Yandex画像検索" in values) add(CompatImageSearchTarget.YANDEX_URL.id)
    }
    return CompatImageSearchTarget.entries.filter { it.id in migrated }
}

internal fun serializeCompatImageSearchTargets(targets: Collection<CompatImageSearchTarget>): String =
    CompatImageSearchTarget.entries.filter { it in targets }.joinToString("|") { it.id }

/** Selection-to-action projection shared by thread, gallery, and viewer. */
internal fun compatImageSearchActionTargets(raw: String?): List<CompatImageSearchTarget> =
    parseCompatImageSearchTargets(raw)

internal fun buildCompatImageSearchTargetUrl(
    target: CompatImageSearchTarget,
    imageUrl: String
): String? {
    if (!isRemoteCompatImageUrl(imageUrl)) return null
    return target.urlTemplate?.replace("%s", imageUrl.encodeURLParameter())
}

internal fun parseCompatImageSearchEngines(raw: String?): List<CompatImageSearchEngine> {
    val values = raw.orEmpty().split('|').map(String::trim).filter(String::isNotBlank).toSet()
    return CompatImageSearchEngine.entries.filter { engine ->
        engine.preferenceName in values || when (engine) {
            CompatImageSearchEngine.TINEYE -> CompatImageSearchTarget.TINEYE_URL.id in values
            CompatImageSearchEngine.IQDB -> CompatImageSearchTarget.IQDB_URL.id in values
            CompatImageSearchEngine.SAUCENAO -> CompatImageSearchTarget.SAUCENAO_URL.id in values
            CompatImageSearchEngine.YANDEX -> CompatImageSearchTarget.YANDEX_URL.id in values
        }
    }
}

internal fun serializeCompatImageSearchEngines(engines: Collection<CompatImageSearchEngine>): String =
    CompatImageSearchEngine.entries.filter { it in engines }.joinToString("|") { it.preferenceName }

/** Builds the public GET URL used by the APK's optional third-party engines. */
internal fun buildCompatImageSearchEngineUrl(
    engine: CompatImageSearchEngine,
    imageUrl: String
): String? {
    if (!isRemoteCompatImageUrl(imageUrl)) return null
    val encoded = imageUrl.encodeURLParameter()
    return when (engine) {
        CompatImageSearchEngine.TINEYE -> "https://tineye.com/search/?url=$encoded"
        CompatImageSearchEngine.IQDB -> "https://iqdb.org/?url=$encoded"
        CompatImageSearchEngine.SAUCENAO -> "https://saucenao.com/search.php?db=999&url=$encoded"
        CompatImageSearchEngine.YANDEX -> "https://yandex.com/images/search?rpt=imageview&url=$encoded"
    }
}

/**
 * Keep Google's legacy URL construction in one place so thread, gallery and
 * viewer actions cannot drift apart from the Lens URL/file modes below.
 */
internal fun buildCompatGoogleImageSearchUrl(imageUrl: String): String? {
    if (!isRemoteCompatImageUrl(imageUrl)) return null
    return "https://www.google.com/searchbyimage?hl=ja&safe=off&client=chrome&image_url=${imageUrl.encodeURLParameter()}"
}

/** Builds the public URL-upload entry point used by Google Lens. */
internal fun buildCompatGoogleLensUrl(imageUrl: String): String? {
    if (!isRemoteCompatImageUrl(imageUrl)) return null
    return "https://lens.google.com/uploadbyurl?url=${imageUrl.encodeURLParameter()}"
}

internal fun isCompatImageSearchableMediaUrl(imageUrl: String, allowGif: Boolean = true): Boolean {
    if (!isRemoteCompatImageUrl(imageUrl)) return false
    val extension = mediaFileExtension(imageUrl)
    if (classifyFutabaMedia(imageUrl) != FutabaMediaKind.IMAGE) return false
    if (!allowGif && extension == "gif") return false
    return true
}

internal fun compatConfiguredFileSearchTargets(
    targets: Collection<CompatImageSearchTarget>
): List<CompatImageSearchTarget> = CompatImageSearchTarget.entries.filter {
    it in targets && it.method == CompatImageSearchMethod.FILE
}

private const val COMPAT_FILE_SEARCH_MAX_UPLOAD_BYTES = 20 * 1024 * 1024
private const val COMPAT_FILE_SEARCH_MAX_RESPONSE_BYTES = 2 * 1024 * 1024
private const val COMPAT_FILE_SEARCH_TIMEOUT_MILLIS = 30_000L
private const val COMPAT_FILE_SEARCH_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36"

/**
 * Executes every File-mode provider exposed by sample/1.apk. IQDB and
 * SauceNAO return provider HTML, which is kept in memory and rendered with a
 * trusted base URL; Yandex returns JSON containing a result identifier.
 */
internal suspend fun searchCompatImageFileTarget(
    httpClient: HttpClient,
    target: CompatImageSearchTarget,
    imageUrl: String,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds()
): Result<CompatImageSearchResult> {
    return try {
        require(target.method == CompatImageSearchMethod.FILE) {
            "この検索先のFile方式は利用できません"
        }
        when (target) {
            CompatImageSearchTarget.GOOGLE_FILE -> CompatImageSearchResult.RemoteUrl(
                target.label,
                searchCompatGoogleClassicFile(httpClient, imageUrl).getOrThrow()
            )
            CompatImageSearchTarget.LENS_FILE -> CompatImageSearchResult.RemoteUrl(
                target.label,
                searchCompatGoogleLensFile(httpClient, imageUrl, nowEpochMillis).getOrThrow()
            )
            CompatImageSearchTarget.IQDB_FILE,
            CompatImageSearchTarget.SAUCENAO_FILE,
            CompatImageSearchTarget.YANDEX_FILE -> withTimeout(COMPAT_FILE_SEARCH_TIMEOUT_MILLIS) {
                val image = downloadCompatSearchImage(httpClient, imageUrl)
                when (target) {
                    CompatImageSearchTarget.IQDB_FILE -> searchCompatMultipartHtmlProvider(
                        httpClient = httpClient,
                        endpoint = "https://iqdb.org/",
                        baseUrl = "https://iqdb.org/",
                        title = "IQDB",
                        image = image
                    )
                    CompatImageSearchTarget.SAUCENAO_FILE -> searchCompatMultipartHtmlProvider(
                        httpClient = httpClient,
                        endpoint = "https://saucenao.com/search.php",
                        baseUrl = "https://saucenao.com/",
                        title = "SauceNAO",
                        image = image,
                        acceptRedirectedResultUrl = true
                    )
                    CompatImageSearchTarget.YANDEX_FILE -> searchCompatYandexFile(
                        httpClient,
                        image
                    )
                }
            }
            else -> error("この検索先のFile方式は利用できません")
        }.let(Result.Companion::success)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
}

private data class CompatSearchUploadImage(
    val bytes: ByteArray,
    val mime: ContentType,
    val fileName: String
)

private suspend fun downloadCompatSearchImage(
    httpClient: HttpClient,
    imageUrl: String
): CompatSearchUploadImage {
    require(isCompatImageSearchableMediaUrl(imageUrl)) { "検索する画像URLが不正です" }
    val response = httpClient.get(imageUrl)
    require(response.status.isSuccess()) { "画像取得 HTTP ${response.status.value}" }
    val declaredSize = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    require(declaredSize == null || declaredSize in 1..COMPAT_FILE_SEARCH_MAX_UPLOAD_BYTES) {
        "画像が大きすぎます（上限20MB）"
    }
    val bytes = readBoundedHttpResponseBytes(
        response,
        COMPAT_FILE_SEARCH_MAX_UPLOAD_BYTES,
        COMPAT_FILE_SEARCH_TIMEOUT_MILLIS
    )
    require(bytes.isNotEmpty()) { "画像が空です" }
    val extension = mediaFileExtension(imageUrl).ifBlank { "jpg" }
    val responseMime = response.headers[HttpHeaders.ContentType]
        ?.substringBefore(';')
        ?.trim()
        ?.takeIf { it.startsWith("image/", ignoreCase = true) }
        ?.let { runCatching { ContentType.parse(it) }.getOrNull() }
    return CompatSearchUploadImage(
        bytes = bytes,
        mime = responseMime ?: compatGoogleLensContentType(extension),
        fileName = "futacha.$extension"
    )
}

private suspend fun searchCompatMultipartHtmlProvider(
    httpClient: HttpClient,
    endpoint: String,
    baseUrl: String,
    title: String,
    image: CompatSearchUploadImage,
    acceptRedirectedResultUrl: Boolean = false
): CompatImageSearchResult {
    val response = httpClient.submitFormWithBinaryData(
        url = endpoint,
        formData = formData {
            append(
                "file",
                image.bytes,
                Headers.build {
                    append(
                        HttpHeaders.ContentDisposition,
                        "form-data; name=\"file\"; filename=\"${image.fileName}\""
                    )
                    append(HttpHeaders.ContentType, image.mime.toString())
                }
            )
        }
    ) {
        header(HttpHeaders.UserAgent, COMPAT_FILE_SEARCH_USER_AGENT)
        header(HttpHeaders.AcceptLanguage, "ja,en-US;q=0.9,en;q=0.8")
        header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
    }
    require(response.status.isSuccess()) { "${title}検索に失敗しました (${response.status.value})" }
    val finalUrl = response.call.request.url.toString()
    val providerHost = Url(baseUrl).host
    require(isTrustedCompatSearchResultUrl(finalUrl, providerHost)) {
        "${title}検索の応答先が不正です"
    }
    if (acceptRedirectedResultUrl && finalUrl.contains('?') && isTrustedCompatSearchResultUrl(finalUrl, "saucenao.com")) {
        return CompatImageSearchResult.RemoteUrl(title, finalUrl)
    }
    val html = readBoundedHttpResponseText(
        response,
        COMPAT_FILE_SEARCH_MAX_RESPONSE_BYTES,
        COMPAT_FILE_SEARCH_TIMEOUT_MILLIS
    )
    require(html.isNotBlank()) { "${title}検索の結果が空です" }
    return CompatImageSearchResult.InlineHtml(title, html, baseUrl)
}

private suspend fun searchCompatYandexFile(
    httpClient: HttpClient,
    image: CompatSearchUploadImage
): CompatImageSearchResult {
    val response = httpClient.post(
        "https://yandex.com/images-apphost/image-download" +
            "?cbird=111&images_avatars_size=preview&images_avatars_namespace=images-cbir"
    ) {
        header(HttpHeaders.UserAgent, COMPAT_FILE_SEARCH_USER_AGENT)
        header(HttpHeaders.AcceptLanguage, "ja,en-US;q=0.9,en;q=0.8")
        header(HttpHeaders.Accept, "application/json, text/plain, */*")
        header(HttpHeaders.Origin, "https://yandex.com")
        header(HttpHeaders.Referrer, "https://yandex.com/images/")
        contentType(image.mime)
        setBody(image.bytes)
    }
    require(response.status.isSuccess()) { "Yandex画像検索に失敗しました (${response.status.value})" }
    val body = readBoundedHttpResponseText(
        response,
        COMPAT_FILE_SEARCH_MAX_RESPONSE_BYTES,
        COMPAT_FILE_SEARCH_TIMEOUT_MILLIS
    )
    val json = Json.parseToJsonElement(body).jsonObject
    val cbirId = json["cbir_id"]?.jsonPrimitive?.content?.trim().orEmpty()
    val resultImageUrl = json["url"]?.jsonPrimitive?.content?.trim().orEmpty()
    require(cbirId.isNotEmpty() && isRemoteCompatImageUrl(resultImageUrl)) {
        "Yandex画像検索の結果を取得できませんでした"
    }
    val resultUrl = URLBuilder("https://yandex.com/images/search").apply {
        parameters.append("rpt", "imageview")
        parameters.append("cbir_id", cbirId)
        parameters.append("url", resultImageUrl)
        parameters.append("cbir_page", "similar")
    }.buildString()
    return CompatImageSearchResult.RemoteUrl("Yandex画像検索", resultUrl)
}

private fun isTrustedCompatSearchResultUrl(raw: String, host: String): Boolean = runCatching {
    val url = Url(raw)
    url.protocol.name in setOf("http", "https") &&
        (url.host.equals(host, ignoreCase = true) || url.host.endsWith(".$host", ignoreCase = true))
}.getOrDefault(false)

private const val ASCII2D_HOST = "ascii2d.net"
private const val ASCII2D_RESPONSE_TIMEOUT_MILLIS = 20_000L
private const val ASCII2D_RESPONSE_MAX_BYTES = 2 * 1024 * 1024
private val ASCII2D_RESULT_REGEX = Regex(
    """https?://(?:[A-Za-z0-9-]+\.)*ascii2d\.net/(?:search|details)/[^\"'<>\\s]+""",
    RegexOption.IGNORE_CASE
)
private val ASCII2D_RELATIVE_RESULT_REGEX = Regex(
    """/(?:search|details)/[^\"'<>\\s]+""",
    RegexOption.IGNORE_CASE
)

internal fun isValidCompatAscii2dEndpoint(raw: String?): Boolean =
    raw?.trim()?.let { value ->
        runCatching {
            val url = Url(value)
            url.protocol.name in setOf("http", "https") && isAscii2dHost(url.host)
        }.getOrDefault(false)
    } == true

internal suspend fun searchCompatAscii2d(
    httpClient: HttpClient,
    endpoint: String,
    imageUrl: String
): Result<String> {
    return try {
        Result.success(withTimeout(ASCII2D_RESPONSE_TIMEOUT_MILLIS) {
            val endpointUrl = endpoint.trim()
            require(isValidCompatAscii2dEndpoint(endpointUrl)) { "ascii2dの検索先が不正です" }
            require(isRemoteCompatImageUrl(imageUrl)) { "検索する画像URLが不正です" }

            val response = httpClient.submitForm(
                url = endpointUrl,
                formParameters = Parameters.build {
                    // ascii2d's URI form accepts the already-public Futaba media URL;
                    // no second download or multipart copy is needed on the client.
                    append("uri", imageUrl)
                }
            )
            val location = response.headers[HttpHeaders.Location]
            if (!response.status.isSuccess() && location == null) {
                error("HTTP ${response.status.value}")
            }
            location?.let(::normalizeCompatAscii2dResultUrl)?.let { return@withTimeout it }

            val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            require(contentLength == null || contentLength <= ASCII2D_RESPONSE_MAX_BYTES) {
                "検索結果が大きすぎます"
            }
            val body = readBoundedHttpResponseText(
                response,
                ASCII2D_RESPONSE_MAX_BYTES,
                ASCII2D_RESPONSE_TIMEOUT_MILLIS
            )
            ASCII2D_RESULT_REGEX.find(body)?.value?.let(::normalizeCompatAscii2dResultUrl)
                ?: ASCII2D_RELATIVE_RESULT_REGEX.find(body)?.value?.let(::normalizeCompatAscii2dResultUrl)
                ?: error("検索結果URLを取得できませんでした")
        })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
}

private fun normalizeCompatAscii2dResultUrl(raw: String): String? {
    val decoded = raw
        .replace("&amp;", "&")
        .trim()
        .trimEnd('.', ',', ';', ')', ']', '}', '"', '\'')
    val absolute = if (decoded.startsWith('/')) "https://$ASCII2D_HOST$decoded" else decoded
    return runCatching {
        val url = Url(absolute)
        absolute.takeIf {
            url.protocol.name in setOf("http", "https") && isAscii2dHost(url.host)
        }
    }.getOrNull()
}

private fun isAscii2dHost(host: String): Boolean =
    host.equals(ASCII2D_HOST, ignoreCase = true) ||
        host.endsWith(".$ASCII2D_HOST", ignoreCase = true)

private const val GOOGLE_LENS_MAX_UPLOAD_BYTES = 20 * 1024 * 1024
private const val GOOGLE_LENS_RESPONSE_MAX_BYTES = 2 * 1024 * 1024
private val compatGoogleLensHtmlResultRegex = Regex(
    """(?i)(?:canonical|og:url)[^>]+(?:href|content)=['\"]([^'\"]+)['\"]"""
)
private const val GOOGLE_LENS_UPLOAD_ENDPOINT = "https://lens.google.com/v3/upload"

/**
 * Downloads the currently displayed public image and submits it through the
 * same multipart field used by Chromium's Lens form (`encoded_image`).
 *
 * The returned URL is the final Lens result URL after the shared client has
 * followed Google's redirect.  We never return an arbitrary redirect or an
 * upload endpoint as a browser URL.
 */
internal suspend fun searchCompatGoogleLensFile(
    httpClient: HttpClient,
    imageUrl: String,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds()
): Result<String> {
    return try {
        Result.success(withTimeout(30_000L) {
            require(isCompatImageSearchableMediaUrl(imageUrl)) { "検索する画像URLが不正です" }
            val imageResponse = httpClient.get(imageUrl)
            if (!imageResponse.status.isSuccess()) {
                error("画像取得 HTTP ${imageResponse.status.value}")
            }
            val declaredSize = imageResponse.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            require(declaredSize == null || declaredSize in 1..GOOGLE_LENS_MAX_UPLOAD_BYTES) {
                "画像サイズが大きすぎます（20MBまで）"
            }
            val imageBytes = readBoundedHttpResponseBytes(
                imageResponse,
                GOOGLE_LENS_MAX_UPLOAD_BYTES,
                30_000L
            )
            require(imageBytes.isNotEmpty()) { "画像が空です" }
            require(imageBytes.size <= GOOGLE_LENS_MAX_UPLOAD_BYTES) {
                "画像サイズが大きすぎます（20MBまで）"
            }

            val extension = mediaFileExtension(imageUrl).ifBlank { "jpg" }
            val uploadUrl = "$GOOGLE_LENS_UPLOAD_ENDPOINT?ep=cntpubb&hl=ja&re=df&s=4&st=$nowEpochMillis"
            val response = httpClient.submitFormWithBinaryData(
                url = uploadUrl,
                formData = formData {
                    append(
                        "encoded_image",
                        imageBytes,
                        Headers.build {
                            append(
                                HttpHeaders.ContentDisposition,
                                "form-data; name=\"encoded_image\"; filename=\"futacha.$extension\""
                            )
                            append(HttpHeaders.ContentType, compatGoogleLensContentType(extension).toString())
                        }
                    )
                }
            )
            val location = response.headers[HttpHeaders.Location]
            if (!response.status.isSuccess() && location == null) {
                error("Lens HTTP ${response.status.value}")
            }
            normalizeCompatGoogleResultUrl(location)?.let { return@withTimeout it }
            normalizeCompatGoogleResultUrl(response.call.request.url.toString())?.let { return@withTimeout it }

            val responseLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            require(responseLength == null || responseLength <= GOOGLE_LENS_RESPONSE_MAX_BYTES) {
                "Lensの応答が大きすぎます"
            }
            val body = readBoundedHttpResponseText(response, GOOGLE_LENS_RESPONSE_MAX_BYTES, 30_000L)
            val htmlUrl = compatGoogleLensHtmlResultRegex.find(body)?.groupValues?.getOrNull(1)
                ?.replace("&amp;", "&")
            normalizeCompatGoogleResultUrl(htmlUrl)
                ?: error("Lensの検索結果URLを取得できませんでした")
        })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
}

internal suspend fun searchCompatGoogleClassicFile(
    httpClient: HttpClient,
    imageUrl: String
): Result<String> = searchCompatGoogleFileUpload(
    httpClient = httpClient,
    imageUrl = imageUrl,
    endpoint = "https://www.google.com/searchbyimage/upload?hl=ja&safe=off",
    extraFieldName = "image_content"
)

private suspend fun searchCompatGoogleFileUpload(
    httpClient: HttpClient,
    imageUrl: String,
    endpoint: String,
    extraFieldName: String
): Result<String> {
    return try {
        Result.success(withTimeout(30_000L) {
            require(isCompatImageSearchableMediaUrl(imageUrl)) { "検索する画像URLが不正です" }
            val imageResponse = httpClient.get(imageUrl)
            require(imageResponse.status.isSuccess()) { "画像取得 HTTP ${imageResponse.status.value}" }
            val imageBytes = readBoundedHttpResponseBytes(
                imageResponse,
                GOOGLE_LENS_MAX_UPLOAD_BYTES,
                30_000L
            )
            require(imageBytes.isNotEmpty()) { "画像が空です" }
            val extension = mediaFileExtension(imageUrl).ifBlank { "jpg" }
            val response = httpClient.submitFormWithBinaryData(
                url = endpoint,
                formData = formData {
                    append(
                        "encoded_image",
                        imageBytes,
                        Headers.build {
                            append(
                                HttpHeaders.ContentDisposition,
                                "form-data; name=\"encoded_image\"; filename=\"futacha.$extension\""
                            )
                            append(HttpHeaders.ContentType, compatGoogleLensContentType(extension).toString())
                        }
                    )
                    append(extraFieldName, "")
                }
            )
            normalizeCompatGoogleResultUrl(response.headers[HttpHeaders.Location])
                ?: normalizeCompatGoogleResultUrl(response.call.request.url.toString())
                ?: error("Google画像検索の結果URLを取得できませんでした")
        })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
}

private fun compatGoogleLensContentType(extension: String): ContentType = when (extension.lowercase()) {
    "jpg", "jpeg", "jpe" -> ContentType.Image.JPEG
    "png" -> ContentType.Image.PNG
    "gif" -> ContentType.Image.GIF
    "webp" -> ContentType.parse("image/webp")
    "avif" -> ContentType.parse("image/avif")
    else -> ContentType.Application.OctetStream
}

private fun normalizeCompatGoogleResultUrl(raw: String?): String? {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return runCatching {
        val url = Url(value)
        val host = url.host.lowercase()
        val trustedHost = host == "lens.google.com" ||
            host == "google.com" ||
            host.endsWith(".google.com")
        val uploadPath = url.encodedPath.contains("/upload", ignoreCase = true)
        value.takeIf {
            url.protocol.name in setOf("http", "https") && trustedHost && !uploadPath
        }
    }.getOrNull()
}

private fun isRemoteCompatImageUrl(raw: String): Boolean =
    runCatching {
        val url = Url(raw.trim())
        url.protocol.name in setOf("http", "https") &&
            url.host.isNotBlank() &&
            url.user.orEmpty().isBlank() &&
            url.password.orEmpty().isBlank()
    }.getOrDefault(false)
