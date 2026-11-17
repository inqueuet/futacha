package com.valoser.futacha.shared.network.aimoge

import com.valoser.futacha.shared.network.ArchiveSearchItem
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLPathPart
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * あいもげの検索（読むだけ）。ふたばの過去ログ検索（inqueuet・ふたポ・フォレスト）には、あいもげの板を渡さない。
 * - 過去ログ：GET /archive/{slug}?q=&page=&limit=  落ちたスレの一覧（本文まで探す）。開く時は今の過去ログの読み込み（snapshot）。
 * - レス検索：GET /search?q=&scope=all            今あるスレのレス（X-Board-Key で板を指定）。
 * どちらも q は1〜100字（研究記録 docs/aimoge-support-research-2026-11-10.md、2026-11-17に実際の応答で確かめた）。
 */

const val AIMOGE_SEARCH_QUERY_MAX_CHARS = 100
private const val AIMOGE_ARCHIVE_SEARCH_MAX_LIMIT = 100

private val aimogeSearchJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

@Serializable
private data class AimogeArchiveListDto(val threads: List<AimogeArchiveThreadDto> = emptyList())

@Serializable
private data class AimogeArchiveThreadDto(
    val threadId: String? = null,
    val opExcerpt: String? = null,
    val thumbnailUrl: String? = null,
    val replyCount: Int? = null,
    val archivedAt: String? = null
)

/** The archived threads of [slug] whose text holds [query], newest first, as the shared past-log rows. */
internal fun parseAimogeArchiveSearch(body: String, slug: String): List<ArchiveSearchItem> {
    val boardUrl = "https://nijiurachan.net/b/$slug/"
    return aimogeSearchJson.decodeFromString(AimogeArchiveListDto.serializer(), body).threads.mapNotNull { dto ->
        val id = dto.threadId?.trim()?.lowercase()?.takeIf { it.matches(AIMOGE_UUID_REGEX) } ?: return@mapNotNull null
        ArchiveSearchItem(
            threadId = id,
            server = "aimoge",
            board = slug,
            // The first line of the OP, as the catalog shows it.
            title = dto.opExcerpt?.lineSequence()?.map(String::trim)?.firstOrNull(String::isNotEmpty),
            htmlUrl = "${boardUrl}thread/$id",
            thumbUrl = dto.thumbnailUrl?.takeIf { it.startsWith("https://") },
            replyCount = dto.replyCount?.coerceAtLeast(0) ?: 0,
            status = "archived",
            finalizedAt = dto.archivedAt?.let { runCatching { kotlin.time.Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
        )
    }.distinctBy { it.threadId }
}

private val AIMOGE_UUID_REGEX = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

@Serializable
private data class AimogeSearchDto(val posts: List<AimogeSearchPostDto> = emptyList())

@Serializable
private data class AimogeSearchAttachmentDto(val thumbnailUrl: String? = null)

@Serializable
private data class AimogeSearchPostDto(
    val threadId: String? = null,
    val seq: Int? = null,
    val status: String? = null,
    val body: String? = null,
    val attachment: AimogeSearchAttachmentDto? = null
)

/**
 * The live threads of [slug] that have a post holding the words (`/search?scope=all`, at most 100 posts, no paging),
 * one row per thread in the order of the first hit. The row's title is the OP's first line when the OP itself hit,
 * else the first line of the first post that hit ("No.{seq}：…"), so the screen shows why the thread was found.
 */
internal fun parseAimogePostSearch(body: String, slug: String): List<ArchiveSearchItem> {
    val boardUrl = "https://nijiurachan.net/b/$slug/"
    val posts = aimogeSearchJson.decodeFromString(AimogeSearchDto.serializer(), body).posts
    val byThread = LinkedHashMap<String, MutableList<AimogeSearchPostDto>>()
    posts.forEach { post ->
        if (post.status != null && post.status != "public") return@forEach
        val id = post.threadId?.trim()?.lowercase()?.takeIf { it.matches(AIMOGE_UUID_REGEX) } ?: return@forEach
        byThread.getOrPut(id) { mutableListOf() } += post
    }
    return byThread.map { (id, hits) ->
        val op = hits.firstOrNull { it.seq == 0 }
        val first = op ?: hits.first()
        val line = first.body?.lineSequence()?.map(String::trim)?.firstOrNull(String::isNotEmpty).orEmpty()
        ArchiveSearchItem(
            threadId = id,
            server = "aimoge",
            board = slug,
            title = if (op != null || first.seq == null) line else "No.${first.seq}：$line",
            htmlUrl = "${boardUrl}thread/$id",
            thumbUrl = (op?.attachment ?: first.attachment)?.thumbnailUrl?.takeIf { it.startsWith("https://") },
            status = "live"
        )
    }
}

/** Reads `/search?q=&scope=all` with [api]: the posts of the live threads. */
suspend fun searchAimogeLiveThreads(api: AimogeApi, slug: String, query: String): List<ArchiveSearchItem> {
    val q = query.trim()
    require(q.isNotEmpty() && q.length <= AIMOGE_SEARCH_QUERY_MAX_CHARS) { "q" }
    val body = api.get("/search?q=${q.encodeURLQueryComponent()}&scope=all", boardKey = slug)
    return withContext(Dispatchers.Default) { parseAimogePostSearch(body, slug) }
}

/**
 * The search of an あいもげ board: the live threads whose posts hold the words first, then the archived ones.
 * A failure of one side is not shown while the other has rows; when both fail, the archive's error is thrown.
 */
suspend fun searchAimogeThreads(api: AimogeApi, slug: String, query: String, limit: Int = 20): List<ArchiveSearchItem> =
    kotlinx.coroutines.coroutineScope {
        val live = async { com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation { searchAimogeLiveThreads(api, slug, query) } }
        val archived = async { com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation { searchAimogeArchiveThreads(api, slug, query, limit) } }
        val liveRows = live.await(); val archivedRows = archived.await()
        if (liveRows.isFailure && archivedRows.isFailure) throw archivedRows.exceptionOrNull()!!
        (liveRows.getOrDefault(emptyList()) + archivedRows.getOrDefault(emptyList())).distinctBy { it.threadId }
    }

/** Reads `/archive/{slug}?q=` with [api]. */
suspend fun searchAimogeArchiveThreads(api: AimogeApi, slug: String, query: String, limit: Int = 20): List<ArchiveSearchItem> {
    val q = query.trim()
    require(q.isNotEmpty() && q.length <= AIMOGE_SEARCH_QUERY_MAX_CHARS) { "q" }
    val body = api.get(
        "/archive/${slug.encodeURLPathPart()}?q=${q.encodeURLQueryComponent()}&page=1&limit=${limit.coerceIn(1, AIMOGE_ARCHIVE_SEARCH_MAX_LIMIT)}",
        boardKey = slug
    )
    return withContext(Dispatchers.Default) { parseAimogeArchiveSearch(body, slug) }
}

/**
 * The same with a client made for this search only (no Cookie, no redirect), closed off the main thread
 * (closing a Ktor client on the main thread can close its sockets there).
 */
suspend fun searchAimogeArchiveThreadsWithOwnClient(
    slug: String,
    query: String,
    limit: Int = 20,
    makeClient: () -> HttpClient = { createAimogeHttpClient(appVersion = "") }
): List<ArchiveSearchItem> = withContext(Dispatchers.Default) {
    val client = makeClient()
    try {
        searchAimogeThreads(AimogeApi(client), slug, query, limit)
    } finally {
        withContext(NonCancellable) { client.close() }
    }
}
