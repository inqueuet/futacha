package com.valoser.futacha.shared.repo.aimoge

import com.valoser.futacha.shared.network.aimoge.*
import com.valoser.futacha.shared.compat.AIMOGE_HOST
import com.valoser.futacha.shared.compat.aimogeBoardShapeOf
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.network.aimoge.AimogeApi
import com.valoser.futacha.shared.network.aimoge.AimogeActionOutcome
import com.valoser.futacha.shared.network.aimoge.AimogeAttachmentInput
import com.valoser.futacha.shared.network.aimoge.AimogeAttachmentSource
import com.valoser.futacha.shared.network.aimoge.AimogeAuthRegistry
import com.valoser.futacha.shared.network.aimoge.AimogeDelChoice
import com.valoser.futacha.shared.network.aimoge.AimogeNotices
import com.valoser.futacha.shared.network.aimoge.AimogePollLinks
import com.valoser.futacha.shared.network.aimoge.AimogePollOutcome
import com.valoser.futacha.shared.network.aimoge.AimogePromptRequest
import com.valoser.futacha.shared.network.aimoge.AimogePrompts
import com.valoser.futacha.shared.network.aimoge.AimogeReaction
import com.valoser.futacha.shared.network.aimoge.AimogeReplyRequest
import com.valoser.futacha.shared.network.aimoge.AimogeReportReason
import com.valoser.futacha.shared.network.aimoge.AimogeThreadRequest
import com.valoser.futacha.shared.network.aimoge.AimogeWriteApi
import com.valoser.futacha.shared.network.aimoge.AimogeWriteOutcome
import com.valoser.futacha.shared.network.aimoge.AimogeWriteRegistry
import com.valoser.futacha.shared.network.aimoge.AimogeWriteRepository
import com.valoser.futacha.shared.network.aimoge.AimogeWriteResult
import com.valoser.futacha.shared.network.aimoge.AIMOGE_POLL_UNAVAILABLE_MESSAGE
import com.valoser.futacha.shared.network.aimoge.AimogeBoardsDto
import com.valoser.futacha.shared.network.aimoge.AimogePostDto
import com.valoser.futacha.shared.network.aimoge.AimogeStateDto
import com.valoser.futacha.shared.network.aimoge.AimogeThreadDto
import com.valoser.futacha.shared.network.aimoge.aimogeEpochMillis
import com.valoser.futacha.shared.network.aimoge.aimogeJson
import com.valoser.futacha.shared.network.aimoge.aimogeThreadPage
import com.valoser.futacha.shared.network.aimoge.decodeAimogeCatalog
import com.valoser.futacha.shared.network.aimoge.decodeAimogeSnapshot
import com.valoser.futacha.shared.network.aimoge.decodeAimogeState
import com.valoser.futacha.shared.network.aimoge.decodeAimogeSummary
import com.valoser.futacha.shared.network.aimoge.decodeAimogeThread
import com.valoser.futacha.shared.network.aimoge.number
import com.valoser.futacha.shared.network.aimoge.toCatalogItem
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.SiteThreadState
import com.valoser.futacha.shared.repo.SiteThreadSummary
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

const val AIMOGE_WRITE_UNAVAILABLE_MESSAGE = "この操作は、あいもげの板ではまだ使えません"
const val AIMOGE_CONSENT_DECLINED_MESSAGE = "削除キーを送らないので、書き込みませんでした。下書きは残っています"
const val AIMOGE_APPROVAL_NEEDED_MESSAGE = "あいもげに書き込むには、設定の「あいもげ」で承認してください。下書きは残っています"
const val AIMOGE_IMAGE_ONLY_DELETE_MESSAGE = "あいもげでは画像だけの削除はできません。レスごと削除してください"
const val AIMOGE_POLL_VOTED_MESSAGE = "アンケートに答えました"
const val AIMOGE_DEL_CANCELLED_MESSAGE = "送りませんでした"

/** A write whose result is not known: never sent again by itself; the screen keeps the draft (the retry sends the same requestId). */
const val AIMOGE_THREAD_CANCELLED_MESSAGE = "スレ立てをやめました"
const val AIMOGE_CLOSE_CONFIRM_MESSAGE = "このスレを閉じますか？閉じると誰も書き込めなくなります（読むことはできます）。元に戻せません。"
const val AIMOGE_CLOSED_MESSAGE = "スレを閉じました"

class AimogeUnconfirmedWriteException(message: String) : NetworkException(message)

internal const val AIMOGE_POST_NOT_FOUND_MESSAGE = "レスが見つかりませんでした。スレを読み直してください"
private val aimogeUuidRegex = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
private val aimogeThreadUrlRegex =
    Regex("^https://nijiurachan\\.net/b/([a-z0-9][a-z0-9_-]{0,31})/thread/([0-9a-f-]{36})/?(?:[?#].*)?$")

/**
 * The mark of an automatic re-read (auto-scroll, read-aloud waiting for new posts, auto-save, foreground patrol). Only
 * the あいもげ repository reads it: such a request does not go out within 15 seconds of the last request for the same
 * thread, nor while the site asked to pause; the copy at hand is returned instead. Futaba boards ignore it.
 */
object AimogeAutomaticFetch : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<AimogeAutomaticFetch>
}

/** Runs [block] as an automatic re-read (see [AimogeAutomaticFetch]). */
suspend fun <T> withAimogeAutomaticFetch(block: suspend () -> T): T = withContext(AimogeAutomaticFetch) { block() }

private suspend fun isAutomaticFetch(): Boolean = currentCoroutineContext()[AimogeAutomaticFetch.Key] != null

/** メール欄の r18 を、R18 のスレを立てられない板で使った時。 */
const val AIMOGE_R18_NOT_ALLOWED_MESSAGE = "この板ではR18のスレを立てられません（メール欄のr18を消してください）"

/** One あいもげ board, as the app lists boards. */
data class AimogeBoardInfo(
    val slug: String,
    val name: String,
    val url: String,
    /** Threads marked R18 may be started on this board (`allowR18ThreadCreation`). */
    val r18: Boolean = false,
    /** Shown in the add-board list at first (`presentation.defaultVisible`). */
    val defaultVisible: Boolean = true,
    /** Checked in the add-board list at first (`presentation.defaultEnabled`). */
    val defaultEnabled: Boolean = true,
    /** The board icon (`presentation.imageUrl`), only when it is on nijiurachan.net over https. */
    val iconUrl: String? = null
)

/** The boards offered for adding: those shown at first (`defaultVisible`), in the site's order, minus [registeredUrls]. */
fun aimogeBoardChoices(boards: List<AimogeBoardInfo>, registeredUrls: Set<String>): List<AimogeBoardInfo> =
    boards.filter { it.defaultVisible && it.url !in registeredUrls }

/** The choices checked at first. */
fun aimogeInitialChecks(choices: List<AimogeBoardInfo>): Set<String> =
    choices.filter { it.defaultEnabled }.mapTo(linkedSetOf()) { it.url }

/** The checked boards that need the R18 confirmation before adding. */
fun aimogeR18Checked(choices: List<AimogeBoardInfo>, checked: Set<String>): List<AimogeBoardInfo> =
    choices.filter { it.r18 && it.url in checked }

/**
 * The [BoardRepository] for あいもげ boards. It only reads; every write throws [AIMOGE_WRITE_UNAVAILABLE_MESSAGE]
 * until the writing batches. [board] arguments are board addresses (`https://nijiurachan.net/b/{slug}/`).
 *
 * Re-reads: a plain [getThread] (opening a thread, the user's refresh) always reads the whole `/thread`. A read marked
 * with [AimogeAutomaticFetch] adds only the new posts from `/state?after`, keeps the gap of [AUTOMATIC_GAP_MILLIS]
 * from the last request, and reads everything again when the copy is older than [FULL_READ_AGE_MILLIS] or the seqs do
 * not join up.
 */
class AimogeBoardRepository private constructor(
    private val clientSource: Lazy<HttpClient>,
    private val nowMillis: () -> Long,
    private val closeClient: Boolean,
    /** True while the site asked to pause (AM-B7); automatic reads then send nothing. */
    private val pausedNow: () -> Boolean,
    /** Called with every error answer of a read (AM-B7: 429 / 5xx start the site pause). */
    private val onReadError: (Throwable) -> Unit
) : BoardRepository {
    constructor(
        client: HttpClient,
        nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
        closeClient: Boolean = false,
        pausedNow: () -> Boolean = { false },
        onReadError: (Throwable) -> Unit = {}
    ) : this(lazyOf(client), nowMillis, closeClient, pausedNow, onReadError)

    private val api by lazy { AimogeApi(clientSource.value) }
    private val mutex = Mutex()
    private val threads = LinkedHashMap<String, Held>()

    /** The copy of one thread held in memory (never saved). */
    private class Held(
        val slug: String,
        val thread: AimogeThreadDto,
        val posts: List<AimogePostDto>,
        val archivedAtMillis: Long?,
        val fullAtMillis: Long,
        val sentAtMillis: Long,
        val page: ThreadPage
    ) {
        val postUuids: Map<String, String> = posts.associate { it.number().toString() to it.id }.mapNotNull { (no, id) -> id?.let { no to it } }.toMap()
        val maxSeq: Long = posts.maxOfOrNull { it.seq } ?: -1
    }

    private fun slugOf(board: String): String =
        aimogeBoardShapeOf(board)?.substringAfter("/b/")?.trim('/')
            ?: throw NetworkException("あいもげの板のアドレスではありません")

    private fun uuidOf(threadId: String): String =
        threadId.trim().lowercase().takeIf { aimogeUuidRegex.matches(it) }
            ?: throw NetworkException("あいもげのスレの番号ではありません")

    private fun <T> decode(block: () -> T): T = try {
        block()
    } catch (e: SerializationException) {
        throw NetworkException("あいもげの応答を読めませんでした", cause = e)
    } catch (e: IllegalArgumentException) {
        throw NetworkException("あいもげの応答を読めませんでした", cause = e)
    }

    private fun threadUrl(slug: String, uuid: String) = "https://$AIMOGE_HOST/b/$slug/thread/$uuid"

    override suspend fun getCatalog(board: String, mode: CatalogMode): List<CatalogItem> = getCatalogPage(board, mode).items

    override suspend fun getCatalogPage(board: String, mode: CatalogMode): com.valoser.futacha.shared.model.CatalogPageContent =
        readCatalog(board, mode, 300)

    private suspend fun readCatalog(board: String, mode: CatalogMode, count: Int): com.valoser.futacha.shared.model.CatalogPageContent = withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
        val slug = slugOf(board)
        val threads = mutableListOf<com.valoser.futacha.shared.network.aimoge.AimogeThreadDto>()
        val wanted = count.coerceIn(1, 3000)
        val pageSize = minOf(wanted, 300)
        var seed: Long? = null
        for (page in 1..((wanted + pageSize - 1) / pageSize)) {
            val body = api.catalog(slug, aimogeSortOf(mode), pageSize, page, seed)
            val dto = decode { decodeAimogeCatalog(body) }
            threads += dto.threads
            seed = dto.seed ?: seed
            if (dto.pagination?.hasNextPage != true || dto.threads.isEmpty()) break
        }
        val pairs = threads.distinctBy { it.id }.take(wanted).map { it to it.toCatalogItem(threadUrl(slug, it.id), slug) }
        val created = HashMap<String, Long>()
        pairs.forEach { (dto, item) -> aimogeEpochMillis(dto.createdAt)?.let { created[item.id] = it } }
        val items = pairs.map { it.second }
        return@withContext com.valoser.futacha.shared.model.CatalogPageContent(
            items = if (mode == CatalogMode.Few) items.sortedBy { it.replyCount } else items,
            threadCreatedAtEpochMillis = created
        )
    }

    override suspend fun getCatalogPageWithSettings(board: String, mode: CatalogMode, settings: com.valoser.futacha.shared.model.CatalogFetchSettings): com.valoser.futacha.shared.model.CatalogPageContent =
        readCatalog(board, mode, settings.approximateThreadCount)

    /** From the copy at hand only; null when the thread was not read here. */
    override suspend fun fetchOpImageUrl(board: String, threadId: String): String? =
        mutex.withLock { threads[threadId.trim().lowercase()] }
            ?.takeIf { aimogeBoardShapeOf(board) == "https://$AIMOGE_HOST/b/${it.slug}/" }
            ?.page?.posts?.firstOrNull()?.imageUrl

    override suspend fun resolveCatalogDisplayTitle(board: String, item: CatalogItem, allowFallbackHeadScan: Boolean): String? = item.title

    override suspend fun getThread(board: String, threadId: String): ThreadPage = try {
        readThread(board, threadId)
    } catch (e: NetworkException) {
        onReadError(e)
        throw e
    }

    private suspend fun readThread(board: String, threadId: String): ThreadPage {
        val slug = slugOf(board)
        val uuid = uuidOf(threadId)
        if (!isAutomaticFetch()) return readFull(slug, uuid)
        val held = mutex.withLock { threads[uuid] }?.takeIf { it.slug == slug }
        val now = nowMillis()
        if (held != null && (pausedNow() || now - held.sentAtMillis < AUTOMATIC_GAP_MILLIS)) return held.page
        if (held == null || now - held.fullAtMillis >= FULL_READ_AGE_MILLIS) return readFull(slug, uuid)
        return readDelta(held, uuid)
    }

    override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent =
        ThreadPageContent(page = getThread(board, threadId))

    private suspend fun readFull(slug: String, uuid: String): ThreadPage = withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
        val sentAt = nowMillis()
        val body = api.thread(uuid, slug)
        val dto = decode { decodeAimogeThread(body) }
        val posts = withPollStates(slug, uuid, dto.posts)
        val page = aimogeThreadPage(dto, posts, nowMillis(), slug)
        hold(uuid, Held(slug, dto.copy(posts = emptyList()), posts, null, sentAt, sentAt, page))
        return@withContext page
    }

    private suspend fun readDelta(held: Held, uuid: String): ThreadPage = withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
        val sentAt = nowMillis()
        val body = api.threadState(uuid, held.maxSeq, held.slug)
        val state = decode { decodeAimogeState(body) }
        val fresh = state.newPosts.sortedBy { it.seq }
        if (fresh.isNotEmpty() && fresh.first().seq > held.maxSeq + 1) return@withContext readFull(held.slug, uuid)
        val bySeq = LinkedHashMap<Long, AimogePostDto>()
        held.posts.forEach { bySeq[it.seq] = it }
        fresh.forEach { row ->
            // U1: an unreadable post comes as {"seq":N,"status":"unavailable"} without an id.
            val old = bySeq[row.seq]
            if (row.id == null) {
                if (old != null) bySeq[row.seq] = old.copy(status = "unavailable")
            } else {
                bySeq[row.seq] = row
            }
        }
        state.postStates.forEach { s ->
            val post = bySeq[s.seq] ?: return@forEach
            bySeq[s.seq] = post.copy(
                status = s.status ?: post.status,
                sodaneCount = s.reactions?.up ?: post.sodaneCount,
                pollState = s.poll ?: post.pollState
            )
        }
        val posts = bySeq.values.filterNot { it.isUnavailableNew(held.maxSeq) }.sortedBy { it.seq }
        val archivedAt = aimogeEpochMillis(state.archivedAt) ?: held.archivedAtMillis
        val base = aimogeThreadPage(held.thread, posts, nowMillis(), held.slug)
        val page = if (archivedAt != null && archivedAt <= nowMillis()) base.copy(archived = true) else base
        hold(uuid, Held(held.slug, held.thread, posts, archivedAt, held.fullAtMillis, sentAt, page))
        return@withContext page
    }

    /**
     * The votes of a thread with a poll: `/thread` has only the definition, so `/state` is read once more (only for
     * such a thread). A failure leaves the poll without votes.
     */
    /** The polls answered here (post UUID → the vote's answer), shown until the server says more. */
    private val answeredPolls = LinkedHashMap<String, AimogePollStateDto>()

    private suspend fun withPollStates(slug: String, uuid: String, posts: List<AimogePostDto>): List<AimogePostDto> {
        if (posts.none { it.poll != null }) return posts
        val maxSeq = posts.maxOfOrNull { it.seq } ?: return posts
        val polls = try {
            api.threadState(uuid, maxSeq, slug).let { body -> decode { decodeAimogeState(body) } }.postStates.mapNotNull { s -> s.poll?.let { s.seq to it } }.toMap()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return posts
        }
        val answered = mutex.withLock { answeredPolls.toMap() }
        return posts.map { p ->
            val fetched = polls[p.seq]
            val mine = answered[p.id]
            val state = if (mine != null) aimogeMergePollState(fetched, mine) else fetched
            state?.let { p.copy(pollState = it) } ?: p
        }
    }

    /** A new post that is already gone has no row to show (as when `/thread` skips its seq). */
    private fun AimogePostDto.isUnavailableNew(oldMax: Long): Boolean = seq > oldMax && status == "unavailable"

    private suspend fun hold(uuid: String, held: Held) = mutex.withLock {
        threads.remove(uuid)
        threads[uuid] = held
        while (threads.size > MAX_THREADS) threads.remove(threads.keys.first())
    }

    /** The post UUID for [postId] in [threadId], reading the thread once more when it is not known; null when absent. */
    internal suspend fun postUuidOf(board: String, threadId: String, postId: String): String? {
        val uuid = uuidOf(threadId)
        mutex.withLock { threads[uuid]?.postUuids?.get(postId) }?.let { return it }
        readFull(slugOf(board), uuid)
        return mutex.withLock { threads[uuid]?.postUuids?.get(postId) }
    }

    override suspend fun getThreadByUrl(threadUrl: String): ThreadPage {
        val (slug, uuid) = threadUrlParts(threadUrl) ?: throw NetworkException("あいもげのスレのアドレスではありません")
        return getThread("https://$AIMOGE_HOST/b/$slug/", uuid)
    }

    private fun threadUrlParts(url: String): Pair<String, String>? =
        aimogeThreadUrlRegex.matchEntire(url.trim().lowercase())?.let { it.groupValues[1] to it.groupValues[2] }
            ?.takeIf { aimogeUuidRegex.matches(it.second) }

    /** Only the posts after the copy at hand (`/state?after`); null when the thread was not read here. */
    override suspend fun getThreadDelta(board: String, threadId: String): ThreadPage? {
        val slug = slugOf(board)
        val uuid = uuidOf(threadId)
        val held = mutex.withLock { threads[uuid] }?.takeIf { it.slug == slug } ?: return null
        return try {
            readDelta(held, uuid)
        } catch (e: NetworkException) {
            onReadError(e)
            throw e
        }
    }

    // ---- Alive checks: `summary`, never HEAD. ----

    private class Known(val summary: SiteThreadSummary, val atMillis: Long)
    private val known = LinkedHashMap<String, Known>()

    private suspend fun knownAnswer(uuid: String): SiteThreadSummary? = mutex.withLock {
        known[uuid]?.takeIf { nowMillis() - it.atMillis < SUMMARY_REUSE_MILLIS }?.summary
    }

    private suspend fun remember(answers: List<SiteThreadSummary>) = mutex.withLock {
        val now = nowMillis()
        answers.filter { it.state != SiteThreadState.UNKNOWN }.forEach {
            known.remove(it.threadId)
            known[it.threadId] = Known(it, now)
        }
        while (known.size > MAX_KNOWN) known.remove(known.keys.first())
    }

    /**
     * Up to 20 threads of one board in one `summary` (reused for [SUMMARY_REUSE_MILLIS]); threads left out of the
     * answer are checked once with `/thread` (404 = gone). An empty array answers nothing. Error answers are thrown.
     */
    override suspend fun getThreadSummaries(board: String, threadIds: List<String>): List<SiteThreadSummary> {
        val slug = slugOf(board)
        val ids = threadIds.map { it.trim().lowercase() }.filter { aimogeUuidRegex.matches(it) }.distinct()
        val reused = ids.mapNotNull { knownAnswer(it) }
        val ask = ids - reused.map { it.threadId }.toSet()
        if (ask.isEmpty()) return reused
        val answers = ArrayList<SiteThreadSummary>()
        ask.chunked(SUMMARY_IDS).forEach { chunk ->
            val found = try {
                api.boardSummary(slug, chunk).let { body -> decode { decodeAimogeSummary(body) } }.associateBy { it.id.lowercase() }
            } catch (e: NetworkException) {
                onReadError(e)
                throw e
            }
            val now = nowMillis()
            chunk.forEach { uuid ->
                val thread = found[uuid]
                answers += when {
                    thread != null -> {
                        val expires = aimogeEpochMillis(thread.expiresAt)
                        if (!thread.isPermanent && expires != null && expires <= now) SiteThreadSummary(uuid, SiteThreadState.GONE)
                        else SiteThreadSummary(uuid, SiteThreadState.ALIVE, postCount = thread.replyCount.coerceAtLeast(0) + 1)
                    }
                    found.isEmpty() -> SiteThreadSummary(uuid, SiteThreadState.UNKNOWN)
                    else -> when (missingThreadAnswer(uuid, slug)) {
                        Alive.GONE -> SiteThreadSummary(uuid, SiteThreadState.GONE)
                        Alive.THERE -> SiteThreadSummary(uuid, SiteThreadState.UNKNOWN)
                        Alive.UNKNOWN -> SiteThreadSummary(uuid, SiteThreadState.UNKNOWN)
                    }
                }
            }
        }
        remember(answers)
        return reused + answers
    }

    override suspend fun probeThreadGone(threadUrl: String): Boolean = probe(threadUrl) == Alive.GONE

    override suspend fun probeThreadExists(threadUrl: String): Boolean = probe(threadUrl) == Alive.THERE

    private enum class Alive { GONE, THERE, UNKNOWN }

    private val pendingProbes = Mutex()
    private val probeQueue = LinkedHashMap<String, MutableList<Pair<String, CompletableDeferred<Alive>>>>()

    private suspend fun probe(threadUrl: String): Alive {
        val (slug, uuid) = threadUrlParts(threadUrl) ?: return Alive.UNKNOWN
        knownAnswer(uuid)?.let { return if (it.state == SiteThreadState.GONE) Alive.GONE else Alive.THERE }
        val answer = CompletableDeferred<Alive>()
        val leader = pendingProbes.withLock {
            val list = probeQueue.getOrPut(slug) { mutableListOf() }
            list += uuid to answer
            list.size == 1
        }
        if (leader) {
            var batch: List<Pair<String, CompletableDeferred<Alive>>> = emptyList()
            try {
                // Let the other checks of the same pass join the batch.
                kotlinx.coroutines.yield()
                kotlinx.coroutines.delay(PROBE_GATHER_MILLIS)
                batch = pendingProbes.withLock { probeQueue.remove(slug).orEmpty() }
                answerProbes(slug, batch)
            } finally {
                // A cancelled or failed leader must not leave the others waiting forever.
                val left = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    pendingProbes.withLock {
                        val queued = probeQueue[slug]
                        if (queued != null && queued.any { it.second === answer }) probeQueue.remove(slug).orEmpty() else emptyList()
                    }
                }
                (batch + left).forEach { it.second.complete(Alive.UNKNOWN) }
            }
        }
        // A wall-clock limit (Default dispatcher): the leader's network work does not run on a test's virtual clock.
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.withTimeoutOrNull(PROBE_WAIT_MILLIS) { answer.await() }
        } ?: Alive.UNKNOWN
    }

    private suspend fun answerProbes(slug: String, batch: List<Pair<String, CompletableDeferred<Alive>>>) {
        val now = nowMillis()
        batch.chunked(SUMMARY_IDS).forEach { chunk ->
            val ids = chunk.map { it.first }.distinct()
            val found = try {
                api.boardSummary(slug, ids).let { body -> decode { decodeAimogeSummary(body) } }.associateBy { it.id.lowercase() }
            } catch (e: CancellationException) {
                chunk.forEach { it.second.complete(Alive.UNKNOWN) }
                throw e
            } catch (e: Exception) {
                chunk.forEach { it.second.complete(Alive.UNKNOWN) }
                return@forEach
            }
            chunk.forEach { (uuid, deferred) ->
                val thread = found[uuid]
                val answer = if (thread != null) {
                    val expires = aimogeEpochMillis(thread.expiresAt)
                    if (!thread.isPermanent && expires != null && expires <= now) Alive.GONE else Alive.THERE
                } else {
                    missingThreadAnswer(uuid, slug)
                }
                deferred.complete(answer)
                if (answer == Alive.GONE) remember(listOf(SiteThreadSummary(uuid, SiteThreadState.GONE)))
                else if (thread != null) remember(listOf(SiteThreadSummary(uuid, SiteThreadState.ALIVE, thread.replyCount.coerceAtLeast(0) + 1)))
            }
        }
    }

    private suspend fun missingThreadAnswer(uuid: String, slug: String): Alive = try {
        api.thread(uuid, slug)
        Alive.THERE
    } catch (e: CancellationException) {
        throw e
    } catch (e: NetworkException) {
        if (e.statusCode == 404) Alive.GONE else Alive.UNKNOWN
    } catch (e: Exception) {
        Alive.UNKNOWN
    }

    // ---- Fallen threads. ----

    /** The archived copy (`snapshot.json`) of a thread `/thread` no longer has; null when there is none. */
    override suspend fun getArchivedThreadContent(board: String, threadId: String): ThreadPageContent? = withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
        val slug = slugOf(board)
        val uuid = uuidOf(threadId)
        val body = try {
            api.archiveSnapshot(slug, uuid)
        } catch (e: NetworkException) {
            if (e.statusCode == 404) return@withContext null
            throw e
        }
        val snapshot = decode { decodeAimogeSnapshot(body) }
        val thread = snapshot.thread ?: return@withContext null
        val page = aimogeThreadPage(thread, snapshot.posts, nowMillis(), slug).copy(archived = true)
        return@withContext ThreadPageContent(page = page)
    }

    /** The boards あいもげ lists. */
    suspend fun boards(): List<AimogeBoardInfo> {
        val body = api.boards()
        return decode { aimogeJson.decodeFromString(AimogeBoardsDto.serializer(), body) }.boards.mapNotNull { b ->
            aimogeBoardShapeOf("https://$AIMOGE_HOST/b/${b.slug}/")?.let {
                AimogeBoardInfo(
                    slug = b.slug,
                    name = b.boardName?.takeIf(String::isNotBlank) ?: b.slug,
                    url = it,
                    r18 = b.allowR18ThreadCreation,
                    defaultVisible = b.presentation?.defaultVisible ?: true,
                    defaultEnabled = b.presentation?.defaultEnabled ?: true,
                    iconUrl = b.presentation?.imageUrl?.takeIf { url -> url.startsWith("https://$AIMOGE_HOST/") }
                )
            }
        }
    }

    // ---- Writes (AM-B9 to AM-B12): the same calls as Futaba, through [AimogeWriteRepository]. ----

    /** Tests set this; the app builds the writer from the read client on the first write. */
    internal var writerOverride: AimogeWriteRepository? = null
    private val writer: AimogeWriteRepository by lazy {
        writerOverride ?: AimogeWriteRepository(AimogeWriteApi(clientSource.value), { AimogeAuthRegistry.auth }, AimogeWriteRegistry.ledger())
    }

    internal var policyOverride: AimogePolicyApi? = null
    private val policyApi: AimogePolicyApi by lazy { policyOverride ?: AimogePolicyApi(clientSource.value) }
    private val policies = mutableMapOf<String, Pair<Long, AimogeClientPolicy>>()
    private val lastThreadAt = mutableMapOf<String, Long>()
    private val lastReplyAt = mutableMapOf<String, Long>()

    /** The board's current non-secret settings, read at most every [POLICY_REUSE_MILLIS]; null when unreadable. */
    suspend fun clientPolicy(board: String): AimogeClientPolicy? {
        val slug = slugOf(board)
        mutex.withLock { policies[slug] }?.let { (at, p) -> if (nowMillis() - at < POLICY_REUSE_MILLIS) return p }
        val fresh = policyApi.clientPolicy(slug) ?: return null
        mutex.withLock { policies[slug] = nowMillis() to fresh }
        return fresh
    }

    /** The lifetimes a new thread may have on [board] now; null when unreadable. */
    suspend fun threadLimits(board: String): AimogeThreadLimits? = policyApi.threadLimits(slugOf(board))

    /** Refuses, before anything is sent, what the board's policy would refuse (the server checks again). */
    private suspend fun checkPolicy(board: String, body: String, attachment: AimogeAttachmentInput?, thread: Boolean, threadId: String?) {
        val policy = clientPolicy(board) ?: return
        val slug = slugOf(board)
        val now = nowMillis()
        val wait = mutex.withLock {
            if (thread) aimogeWaitSeconds(lastThreadAt[slug], policy.threadCreationCooldownSeconds, now)
            else maxOf(
                aimogeWaitSeconds(lastReplyAt[slug], policy.replyCreationCooldownSeconds, now),
                aimogeWaitSeconds(threadId?.let { lastReplyAt["$slug/$it"] }, policy.threadReplyCreationCooldownSeconds, now)
            )
        }
        aimogePolicyRefusal(policy, body, attachment?.bytes, wait)?.let { throw NetworkException(it) }
    }

    private val pollHandler: suspend (String, String) -> String = { postId, optionId -> votePoll(postId, optionId) }

    init {
        AimogePollLinks.register(pollHandler)
    }

    /**
     * Asks at the tap what a write needs (the delete-key consent, the approval, a confirmation) and sends once more
     * after a "yes". A "no" sends nothing and throws, so the screen keeps the draft.
     */
    private suspend fun <T> withPrompts(
        needsConsent: (T) -> Boolean,
        needsApproval: (T) -> Boolean,
        confirmMessage: (T) -> String?,
        call: suspend (confirmed: Boolean) -> T
    ): T {
        var confirmed = false
        repeat(3) {
            val outcome = call(confirmed)
            when {
                needsConsent(outcome) -> {
                    if (!AimogePrompts.ask(AimogePromptRequest.DeleteKeyConsent)) throw NetworkException(AIMOGE_CONSENT_DECLINED_MESSAGE)
                    val auth = AimogeAuthRegistry.auth ?: throw NetworkException(AIMOGE_APPROVAL_NEEDED_MESSAGE)
                    val keySaved = !auth.deleteKey().isNullOrEmpty() ||
                        auth.setDeleteKey(com.valoser.futacha.shared.network.aimoge.newAimogeDeleteKey())
                    if (!keySaved || !auth.setDeleteKeyConsent(true)) {
                        throw NetworkException(com.valoser.futacha.shared.network.aimoge.AIMOGE_SAVE_FAILED_MESSAGE)
                    }
                }
                needsApproval(outcome) -> {
                    AimogePrompts.ask(AimogePromptRequest.Approval)
                    throw NetworkException(AIMOGE_APPROVAL_NEEDED_MESSAGE)
                }
                !confirmed && confirmMessage(outcome) != null -> {
                    val message = confirmMessage(outcome).orEmpty()
                    if (!AimogePrompts.ask(AimogePromptRequest.Confirm(message))) throw NetworkException(message)
                    confirmed = true
                }
                else -> return outcome
            }
        }
        throw NetworkException(AIMOGE_CONSENT_DECLINED_MESSAGE)
    }

    private suspend fun write(call: suspend (Boolean) -> AimogeWriteOutcome): AimogeWriteResult {
        val outcome = withPrompts(
            needsConsent = { it == AimogeWriteOutcome.NeedsConsent },
            needsApproval = { it == AimogeWriteOutcome.NeedsApproval || (it is AimogeWriteOutcome.Failed && it.needsApproval) },
            confirmMessage = { (it as? AimogeWriteOutcome.ConfirmFirst)?.message },
            call = call
        )
        return when (outcome) {
            is AimogeWriteOutcome.Posted -> outcome.result.also { r -> outcome.message?.let(AimogeNotices::show) }
            is AimogeWriteOutcome.Refused -> throw NetworkException(outcome.message)
            is AimogeWriteOutcome.Failed -> throw NetworkException(outcome.message, statusCode = outcome.status)
            is AimogeWriteOutcome.Unconfirmed -> throw AimogeUnconfirmedWriteException(outcome.message)
            is AimogeWriteOutcome.ConfirmFirst -> throw NetworkException(outcome.message)
            AimogeWriteOutcome.NeedsApproval -> throw NetworkException(AIMOGE_APPROVAL_NEEDED_MESSAGE)
            AimogeWriteOutcome.NeedsConsent -> throw NetworkException(AIMOGE_CONSENT_DECLINED_MESSAGE)
        }
    }

    private suspend fun action(call: suspend () -> AimogeActionOutcome): AimogeActionOutcome {
        val outcome = withPrompts(
            needsConsent = { it == AimogeActionOutcome.NeedsConsent },
            needsApproval = { it == AimogeActionOutcome.NeedsApproval },
            confirmMessage = { null },
            call = { call() }
        )
        return when (outcome) {
            is AimogeActionOutcome.Done -> outcome
            is AimogeActionOutcome.Refused -> throw NetworkException(outcome.message)
            is AimogeActionOutcome.Failed -> throw NetworkException(outcome.message, statusCode = outcome.status)
            is AimogeActionOutcome.Unconfirmed -> throw AimogeUnconfirmedWriteException(outcome.message)
            AimogeActionOutcome.NeedsApproval -> throw NetworkException(AIMOGE_APPROVAL_NEEDED_MESSAGE)
            AimogeActionOutcome.NeedsConsent -> throw NetworkException(AIMOGE_CONSENT_DECLINED_MESSAGE)
        }
    }

    /** Nothing is read or sent for a write while あいもげ is off or before the app is approved. */
    private suspend fun ensureWritable() {
        if (!com.valoser.futacha.shared.compat.AimogeAvailability.sitesEnabled) throw NetworkException(com.valoser.futacha.shared.network.aimoge.AIMOGE_SITES_OFF_MESSAGE)
        val auth = AimogeAuthRegistry.auth
        if (auth == null || auth.writeIdentity() == null) {
            AimogePrompts.ask(AimogePromptRequest.Approval)
            throw NetworkException(AIMOGE_APPROVAL_NEEDED_MESSAGE)
        }
    }

    private suspend fun postUuidOrThrow(board: String, threadId: String, postId: String): String {
        ensureWritable()
        return postUuidOf(board, threadId, postId) ?: throw NetworkException(AIMOGE_POST_NOT_FOUND_MESSAGE)
    }

    private suspend fun knownPostIds(threadId: String): Set<String> =
        mutex.withLock { threads[threadId.trim().lowercase()]?.postUuids?.values?.toSet() }.orEmpty()

    override suspend fun voteSaidane(board: String, threadId: String, postId: String) {
        val uuid = postUuidOrThrow(board, threadId, postId)
        action { writer.react(slugOf(board), uuid, AimogeReaction.SOUDANE) }
    }

    /** The del button: del (like Futaba) or the site's report of this post, chosen at the tap. */
    override suspend fun requestDeletion(board: String, threadId: String, postId: String, reasonCode: String) {
        val uuid = postUuidOrThrow(board, threadId, postId)
        when (val choice = AimogePrompts.askDelOrReport() ?: throw NetworkException(AIMOGE_DEL_CANCELLED_MESSAGE)) {
            AimogeDelChoice.Del -> action { writer.react(slugOf(board), uuid, AimogeReaction.DEL) }
            is AimogeDelChoice.Report -> report(board, threadId, postId, postId, choice.reason, choice.detail)
        }
    }

    /** あいもげ deletes the whole post with the stored あいもげ key ([password], the screen's key, only when none is stored). */
    override suspend fun deleteByUser(board: String, threadId: String, postId: String, password: String, imageOnly: Boolean) {
        ensureWritable()
        if (imageOnly) throw NetworkException(AIMOGE_IMAGE_ONLY_DELETE_MESSAGE)
        val uuid = postUuidOrThrow(board, threadId, postId)
        val isOp = mutex.withLock { threads[uuidOf(threadId)]?.posts?.firstOrNull()?.id } == uuid
        if (isOp) {
            // あいもげ can close a thread (no more posts, kept to read): offered where Futaba deletes the thread.
            val choice = AimogePrompts.askChoice(
                AimogeChoiceRequest(
                    "aimoge-op-delete", "スレの1レス目",
                    "自分のスレなら「スレを閉じる」で、読めるまま書き込みだけを止められます。",
                    listOf("スレを閉じる", "1レス目を削除する"), confirmLabel = "実行"
                ),
                whenNoHost = 1
            ) ?: throw NetworkException(AIMOGE_DEL_CANCELLED_MESSAGE)
            if (choice == 0) {
                closeOwnThread(board, threadId)
                return
            }
        }
        // Posts sent from this app carry the あいもげ key; the screen's key (Futaba's) is only a fallback.
        val key = AimogeAuthRegistry.auth?.deleteKey() ?: password.takeIf { it.isNotEmpty() }
        action { writer.deleteOwn(slugOf(board), uuid, key) }
    }

    // Overridden explicitly so the default bodies are never relied on.
    override suspend fun voteSaidaneResponse(board: String, threadId: String, postId: String): String { voteSaidane(board, threadId, postId); return "" }
    override suspend fun requestDeletionResponse(board: String, threadId: String, postId: String, reasonCode: String): String {
        requestDeletion(board, threadId, postId, reasonCode); return ""
    }
    override suspend fun deleteByUserResponse(board: String, threadId: String, postId: String, password: String, imageOnly: Boolean): String {
        deleteByUser(board, threadId, postId, password, imageOnly); return ""
    }

    /**
     * Name, mail (except 「sage」), subject and the screen's delete key are not sent: あいもげ has no such fields and
     * uses its own key. Returns the new post's number (read back once), or null.
     */
    override suspend fun replyToThread(
        board: String, threadId: String, name: String, email: String, subject: String, comment: String, password: String,
        imageFile: ByteArray?, imageFileName: String?, textOnly: Boolean, handwriting: Boolean
    ): String? {
        ensureWritable()
        val slug = slugOf(board)
        val uuid = uuidOf(threadId)
        val attachment = attachmentOf(imageFile, imageFileName, handwriting, textOnly)
        val extras = extraPictures(board, attachment)
        val request = AimogeReplyRequest(
            boardSlug = slug, threadId = uuid, body = comment, sage = email.trim().equals("sage", ignoreCase = true),
            attachment = attachment, extraAttachments = extras, knownPostIds = knownPostIds(uuid)
        )
        checkPolicy(board, comment, attachment, thread = false, threadId = uuid)
        extras.forEach { checkPolicy(board, comment, it, thread = false, threadId = uuid) }
        val result = write { confirmed -> writer.reply(request, confirmed) }
        if (extras.isNotEmpty()) AimogePendingExtras.clear()
        mutex.withLock { val now = nowMillis(); lastReplyAt[slug] = now; lastReplyAt["$slug/$uuid"] = now }
        return runCatching { readFull(slug, uuid) }.onFailure { if (it is CancellationException) throw it }.getOrNull()?.let {
            mutex.withLock { threads[uuid]?.postUuids?.entries?.firstOrNull { e -> e.value == result.postId }?.key }
        }
    }

    /** A new あいもげ thread needs an attachment (J5); returns the thread's UUID. */
    override suspend fun createThread(
        board: String, name: String, email: String, subject: String, comment: String, password: String,
        imageFile: ByteArray?, imageFileName: String?, textOnly: Boolean, handwriting: Boolean
    ): String? {
        ensureWritable()
        val slug = slugOf(board)
        val r18 = aimogeMailRequestsR18(email)
        // Refused before anything is sent: a board that takes no R18 thread would refuse it (or the thread would break the board's rules).
        if (r18 && threadLimits(board)?.allowR18ThreadCreation != true) throw NetworkException(AIMOGE_R18_NOT_ALLOWED_MESSAGE)
        val attachment = attachmentOf(imageFile, imageFileName, handwriting, textOnly)
        checkPolicy(board, comment, attachment, thread = true, threadId = null)
        val extras = extraPictures(board, attachment)
        extras.forEach { checkPolicy(board, comment, it, thread = true, threadId = null) }
        val duration = askDuration(board)
        val request = AimogeThreadRequest(boardSlug = slug, body = comment, attachment = attachment, extraAttachments = extras, duration = duration, r18 = r18)
        val threadId = write { confirmed -> writer.createThread(request, confirmed) }.threadId
        if (extras.isNotEmpty()) AimogePendingExtras.clear()
        mutex.withLock { lastThreadAt[slug] = nowMillis() }
        return threadId
    }

    /**
     * Asks how long the new thread lives (the choices come from `/thread-limits`). Null (the server's longest) when the
     * limits cannot be read or no one is there to ask; cancelling stops the post.
     */
    private suspend fun askDuration(board: String): String? {
        val limits = threadLimits(board) ?: return null
        val choices = aimogeDurationChoices(limits)
        val policy = clientPolicy(board)
        val guide = policy?.let { "送れる形式：${aimogeMimeLabels(it.allowedMimeTypes)}（${aimogeSizeLabel(it.maxFileSizeBytes)}まで）" }.orEmpty()
        val index = AimogePrompts.askChoice(
            AimogeChoiceRequest("aimoge-duration", "スレの保持時間", guide, choices.map { it.label }, confirmLabel = "スレを立てる"),
            whenNoHost = 0
        ) ?: throw NetworkException(AIMOGE_THREAD_CANCELLED_MESSAGE)
        return choices[index].wire.ifEmpty { null }
    }

    /** The 2nd and 3rd pictures the post screen put beside this call ([AimogeExtraPictures]); none without a first. */
    private suspend fun extraPictures(board: String, first: AimogeAttachmentInput?): List<AimogeAttachmentInput> {
        if (first == null) return emptyList()
        val pictures = kotlin.coroutines.coroutineContext[AimogeExtraPictures]?.pictures ?: AimogePendingExtras.of(board)
        return pictures.filter { it.first.isNotEmpty() }
            .map { (bytes, name) -> AimogeAttachmentInput(bytes, name, AimogeAttachmentSource.FILE) }
    }

    private fun attachmentOf(bytes: ByteArray?, fileName: String?, handwriting: Boolean, textOnly: Boolean): AimogeAttachmentInput? {
        if (textOnly || bytes == null || bytes.isEmpty()) return null
        return AimogeAttachmentInput(bytes, fileName ?: "image", if (handwriting) AimogeAttachmentSource.OEKAKI else AimogeAttachmentSource.FILE)
    }

    /** Answers the poll on post [postId] (a UUID from a poll link) and reads the thread again for the votes. */
    internal suspend fun votePoll(postId: String, optionId: String): String {
        val held = mutex.withLock { threads.entries.firstOrNull { postId in it.value.postUuids.values } }
            ?: return AIMOGE_POLL_UNAVAILABLE_MESSAGE
        val slug = held.value.slug
        val option = held.value.posts.firstOrNull { it.id == postId }
            ?.poll?.options?.firstOrNull { it.id == optionId }
            ?: return AIMOGE_POLL_UNAVAILABLE_MESSAGE
        if (!AimogePrompts.ask(AimogePromptRequest.Confirm("「${option.label}」に投票しますか？"))) {
            return "アンケートの回答を取り消しました"
        }
        var message = AIMOGE_POLL_VOTED_MESSAGE
        val outcome = withPrompts(
            needsConsent = { (it as? AimogePollOutcome.Other)?.outcome == AimogeActionOutcome.NeedsConsent },
            needsApproval = { (it as? AimogePollOutcome.Other)?.outcome == AimogeActionOutcome.NeedsApproval },
            confirmMessage = { null },
            call = { writer.votePoll(slug, postId, optionId) }
        )
        // The public `/state` does not know who is reading, so an open poll comes back without the answer or the
        // results; the Web version shows the results from the vote's own answer. Keep it (thread 01a1cd5d #72).
        val answered = when (outcome) {
            is AimogePollOutcome.Voted -> outcome.state
            is AimogePollOutcome.Rejected -> outcome.state
            is AimogePollOutcome.Other -> null
        }
        answered?.let { state -> mutex.withLock { answeredPolls[postId] = state.toDto() } }
        when (outcome) {
            is AimogePollOutcome.Voted -> Unit
            is AimogePollOutcome.Rejected -> message = outcome.message
            is AimogePollOutcome.Other -> message = when (val o = outcome.outcome) {
                is AimogeActionOutcome.Refused -> o.message
                is AimogeActionOutcome.Failed -> o.message
                is AimogeActionOutcome.Unconfirmed -> o.message
                AimogeActionOutcome.NeedsApproval -> AIMOGE_APPROVAL_NEEDED_MESSAGE
                AimogeActionOutcome.NeedsConsent -> AIMOGE_CONSENT_DECLINED_MESSAGE
                is AimogeActionOutcome.Done -> AIMOGE_POLL_VOTED_MESSAGE
            }
        }
        runCatching { readFull(slug, held.key) }.onFailure { if (it is CancellationException) throw it }
        return message
    }

    /** Reports posts [fromNumber]..[toNumber] (the numbers on screen) of [threadId]. */
    suspend fun report(board: String, threadId: String, fromNumber: String, toNumber: String, reason: AimogeReportReason, detail: String) {
        ensureWritable()
        val uuid = uuidOf(threadId)
        val seqs = mutex.withLock { threads[uuid] }?.posts?.associate { it.number().toString() to it.seq }.orEmpty()
        val from = seqs[fromNumber] ?: throw NetworkException(AIMOGE_POST_NOT_FOUND_MESSAGE)
        val to = seqs[toNumber] ?: throw NetworkException(AIMOGE_POST_NOT_FOUND_MESSAGE)
        action { writer.report(slugOf(board), uuid, minOf(from, to), maxOf(from, to), reason, detail) }
    }

    /** Closes the person's own thread with the stored あいもげ key. */
    suspend fun closeOwnThread(board: String, threadId: String) {
        ensureWritable()
        val confirmed = AimogePrompts.ask(AimogePromptRequest.Confirm(AIMOGE_CLOSE_CONFIRM_MESSAGE))
        if (!confirmed) throw NetworkException(AIMOGE_DEL_CANCELLED_MESSAGE)
        action { writer.closeThread(slugOf(board), uuidOf(threadId)) }
        AimogeNotices.show(AIMOGE_CLOSED_MESSAGE)
        runCatching { readFull(slugOf(board), uuidOf(threadId)) }.onFailure { if (it is CancellationException) throw it }
    }

    override fun close() {
        // A short-lived repository (the board picker) must not take the poll links away from the app's one.
        AimogePollLinks.unregister(pollHandler)
        if (closeClient && clientSource.isInitialized()) clientSource.value.close()
    }
    override fun closeAsync(): Job { close(); return CompletableDeferred(Unit) }
    override suspend fun clearOpImageCache(board: String?, threadId: String?) {
        mutex.withLock { if (threadId == null) threads.clear() else threads.remove(threadId.trim().lowercase()) }
    }
    override suspend fun invalidateCookies(board: String) = Unit

    companion object {
        /** A repository that makes its own cookie-less client on the first request and closes it with [close]. */
        fun owningClient(
            makeClient: () -> HttpClient,
            nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
            pausedNow: () -> Boolean = { false },
            onReadError: (Throwable) -> Unit = {}
        ): AimogeBoardRepository = AimogeBoardRepository(lazy(makeClient), nowMillis, closeClient = true, pausedNow = pausedNow, onReadError = onReadError)

        /** How long a `summary` answer is reused, so the stages of one background run send it once. */
        const val SUMMARY_REUSE_MILLIS = 90_000L
        const val POLICY_REUSE_MILLIS = 10 * 60_000L

        const val AUTOMATIC_GAP_MILLIS = 15_000L
        const val FULL_READ_AGE_MILLIS = 10 * 60_000L
        private const val MAX_THREADS = 8
        private const val MAX_KNOWN = 2_000
        private const val SUMMARY_IDS = 20
        private const val PROBE_GATHER_MILLIS = 30L
        private const val PROBE_WAIT_MILLIS = 60_000L
    }
}

/** The あいもげ `sort` for each catalog tab; 少ない順 is sorted here, 監視 filtered by the caller. */
internal fun aimogeSortOf(mode: CatalogMode): String = when (mode) {
    CatalogMode.Catalog, CatalogMode.WatchWords -> "bump"
    CatalogMode.New -> "new"
    CatalogMode.Old -> "old"
    CatalogMode.Many, CatalogMode.Few -> "replies"
    CatalogMode.Momentum -> "momentum"
    CatalogMode.So -> "soudane"
}
