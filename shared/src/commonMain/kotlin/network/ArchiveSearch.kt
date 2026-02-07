package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.model.BoardSummary
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

data class ArchiveSearchScope(val server: String, val board: String)

@Serializable
data class ArchiveSearchItem(
    val threadId: String,
    val server: String,
    val board: String,
    val title: String? = null,
    val htmlUrl: String,
    val thumbUrl: String? = null,
    val status: String? = null,
    @Serializable(with = ArchiveSearchTimestampSerializer::class)
    val createdAt: Long? = null,
    @Serializable(with = ArchiveSearchTimestampSerializer::class)
    val finalizedAt: Long? = null,
    @Serializable(with = ArchiveSearchTimestampSerializer::class)
    val uploadedAt: Long? = null
)

@Serializable
data class ArchiveSearchResponse(
    val query: String? = null,
    val filter: String? = null,
    val count: Int? = null,
    val results: List<ArchiveSearchItem> = emptyList()
)

private const val MAX_ARCHIVE_RESPONSE_SIZE = 2 * 1024 * 1024 // 2MB

object ArchiveSearchTimestampSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ArchiveSearchTimestamp", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long? {
        val jsonDecoder = decoder as? JsonDecoder ?: return runCatching { decoder.decodeLong() }.getOrNull()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return null
        val primitive = element.jsonPrimitive
        return primitive.content.toLongOrNull()
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeLong(value)
        }
    }
}

fun extractArchiveSearchScope(board: BoardSummary?): ArchiveSearchScope? {
    return extractArchiveSearchScope(board?.url)
}

fun extractArchiveSearchScope(boardUrl: String?): ArchiveSearchScope? {
    if (boardUrl.isNullOrBlank()) return null
    return runCatching {
        val normalizedUrl = resolveBaseUrlFromThreadUrl(boardUrl) ?: boardUrl
        val baseUrl = BoardUrlResolver.resolveBoardBaseUrl(normalizedUrl)
        val parsed = Url(baseUrl)
        val server = parsed.host.substringBefore('.', parsed.host).ifBlank { return null }
        val boardSlug = BoardUrlResolver.resolveBoardSlug(normalizedUrl).ifBlank { return null }
        ArchiveSearchScope(server = server, board = boardSlug)
    }.getOrNull()
}

fun buildArchiveSearchUrl(
    query: String,
    scope: ArchiveSearchScope?
): String {
    val params = buildList<String> {
        if (query.isNotBlank()) {
            add("q=${query.encodeURLParameter()}")
        }
        scope?.let {
            add("server=${it.server.encodeURLParameter()}")
            add("board=${it.board.encodeURLParameter()}")
        }
    }
    return buildString {
        append("https://spider.serendipity01234.workers.dev/search")
        if (params.isNotEmpty()) {
            append("?")
            append(params.joinToString("&"))
        }
    }
}

suspend fun fetchArchiveSearchResults(
    httpClient: HttpClient,
    query: String,
    scope: ArchiveSearchScope?,
    json: Json
): List<ArchiveSearchItem> {
    val url = buildArchiveSearchUrl(query, scope)
    val response = httpClient.get(url)
    if (!response.status.isSuccess()) {
        throw IllegalStateException("検索に失敗しました: ${response.status}")
    }
    val contentLength = response.headers["Content-Length"]?.toLongOrNull()
    if (contentLength != null && contentLength > MAX_ARCHIVE_RESPONSE_SIZE) {
        throw IllegalStateException("検索結果が大きすぎます")
    }
    val body = response.bodyAsText()
    if (body.length > MAX_ARCHIVE_RESPONSE_SIZE) {
        throw IllegalStateException("検索結果が大きすぎます")
    }
    return parseArchiveSearchResults(body, scope, json)
}

fun parseArchiveSearchResults(
    body: String,
    scope: ArchiveSearchScope?,
    json: Json
): List<ArchiveSearchItem> {
    val element = runCatching { json.parseToJsonElement(body) }.getOrElse {
        throw IllegalStateException("検索結果の解析に失敗しました")
    }
    val itemsElement = when (element) {
        is JsonArray -> element
        is JsonObject -> {
            element["results"]
                ?: element["items"]
                ?: element["data"]
                ?: element["threads"]
        }
        else -> null
    }
    val items = itemsElement as? JsonArray
        ?: throw IllegalStateException("検索結果の形式が不明です")
    return items.mapNotNull { parseArchiveSearchItem(it, scope) }
}

fun parseArchiveSearchItem(
    element: JsonElement,
    scope: ArchiveSearchScope?
): ArchiveSearchItem? {
    val obj = element as? JsonObject ?: return null
    val htmlUrl = obj.firstString("htmlUrl", "html_url", "url", "link", "href") ?: return null
    val threadId = obj.firstString("threadId", "thread_id", "id", "thread")
        ?: extractThreadIdFromUrl(htmlUrl)
        ?: return null
    val server = obj.firstString("server", "srv") ?: scope?.server.orEmpty()
    val board = obj.firstString("board", "boardId", "brd") ?: scope?.board.orEmpty()
    val title = obj.firstString("title", "subject")
    val thumbUrl = obj.firstString("thumbUrl", "thumb_url", "thumb", "thumbnail", "image")
    val status = obj.firstString("status", "state")
    val createdAt = obj.firstLong("createdAt", "created_at", "created")
    val finalizedAt = obj.firstLong("finalizedAt", "finalized_at", "finalized")
    val uploadedAt = obj.firstLong("uploadedAt", "uploaded_at", "uploaded")
    return ArchiveSearchItem(
        threadId = threadId,
        server = server,
        board = board,
        title = title,
        htmlUrl = htmlUrl,
        thumbUrl = thumbUrl,
        status = status,
        createdAt = createdAt,
        finalizedAt = finalizedAt,
        uploadedAt = uploadedAt
    )
}

fun selectLatestArchiveMatch(
    items: List<ArchiveSearchItem>,
    threadId: String
): ArchiveSearchItem? {
    return items
        .asSequence()
        .filter { it.threadId == threadId }
        .maxByOrNull { item ->
            item.uploadedAt ?: item.finalizedAt ?: item.createdAt ?: 0L
        }
}

private fun JsonObject.firstString(vararg keys: String): String? {
    keys.forEach { key ->
        val value = this[key]?.asStringOrNull()?.trim()
        if (!value.isNullOrEmpty()) {
            return value
        }
    }
    return null
}

private fun JsonObject.firstLong(vararg keys: String): Long? {
    keys.forEach { key ->
        val value = this[key]?.asLongOrNull()
        if (value != null) {
            return value
        }
    }
    return null
}

private fun JsonElement.asStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (this is JsonNull) return null
    val content = primitive.content
    return if (content == "null") null else content
}

private fun JsonElement.asLongOrNull(): Long? {
    val primitive = this as? JsonPrimitive ?: return null
    if (this is JsonNull) return null
    return primitive.content.toLongOrNull()
}

private fun extractThreadIdFromUrl(url: String): String? {
    val primary = Regex("""/res/(\d+)\.htm""").find(url)?.groupValues?.getOrNull(1)
    if (!primary.isNullOrBlank()) return primary
    return Regex("""(\d+)(?:\.htm)?$""").find(url)?.groupValues?.getOrNull(1)
}

private fun resolveBaseUrlFromThreadUrl(threadUrl: String): String? {
    return runCatching {
        val url = Url(threadUrl)
        val segments = url.encodedPath.split('/').filter { it.isNotBlank() }
        val boardSegments = segments.takeWhile { it.lowercase() != "res" }
        if (boardSegments.isEmpty()) return null
        val path = "/" + boardSegments.joinToString("/")
        buildString {
            append(url.protocol.name)
            append("://")
            append(url.host)
            if (url.port != url.protocol.defaultPort) {
                append(":${url.port}")
            }
            append(path.trimEnd('/'))
        }
    }.getOrNull()
}
