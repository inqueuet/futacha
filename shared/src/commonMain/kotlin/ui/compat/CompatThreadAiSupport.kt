package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyListState
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.ai.*
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal data class CompatThreadAiState(
    val summaryEnabled: Boolean = false,
    val moderationEnabled: Boolean = false,
    val running: Boolean = false,
    val summary: ThreadSummary? = null,
    val summaryError: String? = null,
    val moderationError: String? = null,
    val posts: List<Post> = emptyList(),
    val results: List<PostModerationResult> = emptyList(),
    val hiddenPostNos: Set<String> = emptySet(),
    val candidatePostNos: Set<String> = emptySet()
)

internal data class CompatThreadAiModerationPart(
    val running: Boolean = false,
    val posts: List<Post> = emptyList(),
    val results: List<PostModerationResult> = emptyList(),
    val error: String? = null
)

internal data class CompatThreadAiSummaryPart(
    val running: Boolean = false,
    val summary: ThreadSummary? = null,
    val error: String? = null
)

private class CompatModerationSource(
    val posts: List<Post>,
    val source: List<Post>,
    val context: LocalModerationContext,
    val cacheKeys: Map<String, ThreadPostModerationCacheKey>
)

/** Effect identity excludes fetch times, votes and media presentation metadata. */
internal fun buildCompatThreadAiSourceKey(snapshot: CompatThreadSnapshot): CompatThreadSnapshot = snapshot.copy(
    revision = 0, fetchedAtEpochMillis = 0, boardTitle = null, expiresAtLabel = null,
    deletedNotice = null, truncationReason = null,
    posts = snapshot.posts.map { it.copy(imageUrl = null, thumbnailUrl = null, saidaneLabel = null,
        referencedCount = 0, thumbnailWidth = null, thumbnailHeight = null, imageFileSizeBytes = null,
        mediaKey = null, quoteReferences = emptyList()) }
)

private const val COMPAT_AI_AVAILABLE_TTL_MS = 5 * 60_000L
private const val COMPAT_AI_UNAVAILABLE_TTL_MS = 30_000L
internal const val COMPAT_AI_MODERATION_EXHAUSTED =
    "AIが判断できなかったレスがあります。荒らし非表示をOFFにしてからONへ戻すと判定し直します。"
internal const val COMPAT_AI_SUMMARY_FALLBACK =
    "端末AIで要約できなかったため、本文から抜き出した内容を表示しています。再試行できます。"

/** Per-screen caches: no board history, NG rules or source snapshots are modified. */
internal class CompatThreadAiSession(
    private val service: OnDeviceAiService,
    private val timeSource: TimeSource = TimeSource.Monotonic
) {
    private val summaries = linkedMapOf<String, ThreadSummary>()
    private val moderation = linkedMapOf<ThreadPostModerationCacheKey, PostModerationResult>()
    private val cacheMutex = Mutex()
    // Shared with the hybrid moderation's device side, so a summary and a device batch queue
    // for the same device AI and each deadline covers only its own inference.
    private val localInferenceMutex = (service as? RoutedAiService)?.localInferenceLock ?: Mutex()
    private class AvailabilityCache {
        val mutex = Mutex()
        var value: AiAvailability? = null
        var checkedAt: TimeMark? = null
    }
    private val taskAvailability = service as? AiTaskAvailability
    private val summaryAvailability = AvailabilityCache()
    // With task-specific checks, moderation never waits behind the summary provider's check.
    private val moderationAvailability = if (taskAvailability == null) summaryAvailability else AvailabilityCache()
    // Device-only posts the model left undecided: re-inferred a bounded number of times per input.
    private val retryBudget = ModerationRetryBudget()
    private var retryGeneration = 0

    /** Cached per session: on Android each check is an IPC to the AI process. Failures are not cached. */
    suspend fun availability(forModeration: Boolean = false): Result<AiAvailability> {
        val cache = if (forModeration) moderationAvailability else summaryAvailability
        return cache.mutex.withLock {
            val cached = cache.value
            val checkedAt = cache.checkedAt
            val supported = if (forModeration) cached?.supportsPostModeration else cached?.supportsThreadSummary
            val ttl = if (supported == true) COMPAT_AI_AVAILABLE_TTL_MS else COMPAT_AI_UNAVAILABLE_TTL_MS
            if (cached != null && checkedAt != null && checkedAt.elapsedNow().inWholeMilliseconds < ttl) {
                return@withLock Result.success(cached)
            }
            aiAttempt {
                withContext(AppDispatchers.io) {
                    when {
                        taskAvailability == null -> service.getAvailability()
                        forModeration -> taskAvailability.moderationAvailability()
                        else -> taskAvailability.summaryAvailability()
                    }
                }
            }.onSuccess {
                cache.value = it
                cache.checkedAt = timeSource.markNow()
            }.onFailure {
                cache.value = null
                cache.checkedAt = null
            }
        }
    }

    // Queueing is cancellable but is not charged to the model's execution deadline.
    private suspend fun <T> inferenceAttempt(
        external: Boolean, timeout: Long = 45_000L, block: suspend () -> T
    ): Result<T> = if (external) aiAttempt(timeout, block)
    else localInferenceMutex.withLock { aiAttempt(timeout, block) }

    /** One-shot analysis for a fixed viewport (`null` = all posts): moderation, then summary. */
    suspend fun analyze(
        snapshot: CompatThreadSnapshot,
        title: String,
        summaryEnabled: Boolean,
        moderationEnabled: Boolean,
        nearbyPostIds: Set<String>? = null,
        publish: (CompatThreadAiState) -> Unit
    ) {
        if (!summaryEnabled && !moderationEnabled) return
        var state = CompatThreadAiState(summaryEnabled, moderationEnabled, running = true)
        publish(state)
        if (moderationEnabled) {
            moderate(snapshot, title, flowOf(nearbyPostIds)) { part ->
                state = state.copy(posts = part.posts, results = part.results, moderationError = part.error)
                publish(state)
            }
        }
        if (summaryEnabled) {
            summarize(snapshot, title) { part ->
                state = state.copy(summary = part.summary, summaryError = part.error)
                publish(state)
            }
        }
        publish(state.copy(running = false))
    }

    /**
     * Follows [viewport] without restarting: a viewport change only queues newly nearby posts and
     * never cancels the batch in flight. Context and cache keys are built off the main thread.
     */
    suspend fun moderate(
        snapshot: CompatThreadSnapshot,
        title: String,
        viewport: Flow<Set<String>?>,
        retry: Int = 0,
        resume: Flow<*> = emptyFlow<Unit>(),
        publish: (CompatThreadAiModerationPart) -> Unit
    ) {
        // An explicit retry may re-infer every undecided post again.
        if (retry != retryGeneration) { retryGeneration = retry; retryBudget.clear() }
        val withoutContext = service.externalModeration && !service.hybridModeration
        val configurationKey = service.configurationKey
        val prepared = withContext(AppDispatchers.parsing) {
            val posts = snapshot.toThreadPage(snapshot.tabKey).posts
            val source = resolveThreadAiPostModerationSourcePosts(posts)
            val context = LocalModerationContext(title, source)
            CompatModerationSource(posts, source, context, source.associate { post ->
                post.id to buildThreadPostModerationCacheKey(snapshot.tabKey, post, configurationKey,
                    if (withoutContext) "" else context.forPosts(listOf(post)))
            })
        }
        // Session decisions include the configuration revision; OpenAI also caches raw scores.
        val results = cacheMutex.withLock {
            withContext(AppDispatchers.parsing) {
                linkedMapOf<String, PostModerationResult>().apply {
                    prepared.source.forEach { post -> prepared.cacheKeys[post.id]?.let(moderation::get)?.let { put(post.id, it) } }
                }
            }
        }
        var part = CompatThreadAiModerationPart(running = true, posts = prepared.posts, results = results.values.toList())
        publish(part)
        runViewportModeration(
            viewport = viewport,
            retryDelayMillis = if (service.externalModeration && !service.hybridModeration) 0L else 1_500L,
            // External answers may be partial and are retried; device-only inputs are bounded.
            retryBudget = if (service.externalModeration) null else retryBudget,
            retryKey = { post -> prepared.cacheKeys[post.id] ?: post.id },
            onViewportChanged = { part = part.copy(error = null) },
            resume = resume,
            nextBatch = nextBatch@{ nearby, attempted ->
                val availability = availability(forModeration = true).getOrElse {
                    part = part.copy(running = false, error = "AIの利用状況を確認できませんでした。")
                    publish(part)
                    return@nextBatch null
                }
                if (!availability.supportsPostModeration) {
                    part = part.copy(running = false, error = availability.unavailableReason ?: "荒らし判定を利用できません。")
                    publish(part)
                    return@nextBatch null
                }
                // Batches hold at most 32 posts, so the head of the queue decides the next batch.
                val pending = prepared.source.asSequence().filter { post ->
                    results[post.id]?.isComplete != true && post.id !in attempted && (nearby == null || post.id in nearby)
                }.take(32).toList()
                val batch = when {
                    pending.isEmpty() -> null
                    withoutContext -> PostModerationInput(snapshot.tabKey, pending)
                    else -> withContext(AppDispatchers.parsing) {
                        prepared.context.batches(snapshot.tabKey, pending).firstOrNull()
                    }
                }
                part = part.copy(running = batch != null)
                publish(part)
                batch
            },
            runBatch = { input ->
                val accepted = linkedMapOf<String, PostModerationResult>()
                fun acceptDecisions(classified: List<PostModerationResult>) {
                    val byId = classified.associateBy { it.postId }
                    var changed = false
                    for (post in input.posts) {
                        val decision = byId[post.id] ?: continue
                        accepted[post.id] = decision
                        results[post.id] = decision
                        changed = true
                    }
                    if (changed) {
                        part = part.copy(results = results.values.toList())
                        publish(part)
                    }
                }
                val result = try {
                    inferenceAttempt(service.externalModeration, if (service.externalModeration) 150_000L else 45_000L) {
                        // OpenAI reads and writes its score cache here; keep it off the main thread.
                        withContext(AppDispatchers.io + ModerationPostContext(prepared.context)) {
                            service.classifyPosts(input, ::acceptDecisions).getOrThrow()
                        }
                    }.onSuccess(::acceptDecisions) // Before the commit below, so final decisions are cached.
                } finally {
                    if (accepted.isNotEmpty()) withContext(NonCancellable) {
                        cacheMutex.withLock {
                            accepted.forEach { (id, decision) ->
                                prepared.cacheKeys[id]?.let { key ->
                                    putBoundedAiCacheEntry(moderation, key, decision,
                                        maxOf(THREAD_AI_POST_MODERATION_CACHE_MAX_ENTRIES, prepared.posts.size))
                                }
                            }
                        }
                    }
                }
                result.onSuccess {
                    val undecided = input.posts.filter { post -> accepted[post.id]?.isComplete != true }
                    if (!service.externalModeration) undecided.forEach { retryBudget.record(prepared.cacheKeys[it.id] ?: it.id) }
                    // Posts whose retries are used up are not retried by moving the viewport.
                    val exhausted = !service.externalModeration &&
                        undecided.none { retryBudget.allows(prepared.cacheKeys[it.id] ?: it.id) }
                    if (undecided.isNotEmpty()) part = part.copy(error = if (exhausted) COMPAT_AI_MODERATION_EXHAUSTED
                        else "一部の判定は未完了です。表示位置を移して戻すと未判定分を再試行します。")
                }.onFailure {
                    part = part.copy(running = false, error = "一部のレスを判定できませんでした。再試行できます。")
                }
                part = part.copy(results = results.values.toList())
                publish(part)
                !(result.isFailure && service.externalModeration)
            }
        )
        part = part.copy(running = false)
        publish(part)
    }

    /** Turning moderation off and on again re-judges posts whose retries were used up. */
    fun clearModerationRetries() = retryBudget.clear()

    suspend fun summarize(
        snapshot: CompatThreadSnapshot,
        title: String,
        publish: (CompatThreadAiSummaryPart) -> Unit
    ) {
        publish(CompatThreadAiSummaryPart(running = true))
        val availability = availability().getOrElse {
            publish(CompatThreadAiSummaryPart(error = "AIの利用状況を確認できませんでした。"))
            return
        }
        if (!availability.supportsThreadSummary) {
            publish(CompatThreadAiSummaryPart(error = availability.unavailableReason ?: "この端末ではスレ要約を利用できません。"))
            return
        }
        val (input, cacheKey) = withContext(AppDispatchers.parsing) {
            val posts = snapshot.toThreadPage(snapshot.tabKey).posts
            val input = ThreadSummaryInput(snapshot.tabKey, title, posts, snapshot.tabKey, snapshot.isTruncated)
            input to (snapshot.tabKey + "\n" + title + "\n" + snapshot.isTruncated + "\n" + buildThreadSummarySourceText(input))
        }
        val cached = cacheMutex.withLock { summaries[cacheKey] }
        val result = if (cached != null) Result.success(cached) else inferenceAttempt(external = false) {
            withContext(AppDispatchers.io) { service.summarizeThread(input).getOrThrow() }
        }
        result.onSuccess {
            // A summary extracted because the model failed is shown, not kept: a retry regenerates it.
            if (it.isFallbackThreadSummary) {
                publish(CompatThreadAiSummaryPart(summary = it, error = COMPAT_AI_SUMMARY_FALLBACK))
                return
            }
            cacheMutex.withLock { putBoundedAiCacheEntry(summaries, cacheKey, it, THREAD_AI_CACHE_MAX_ENTRIES) }
            publish(CompatThreadAiSummaryPart(summary = it))
        }.onFailure { publish(CompatThreadAiSummaryPart(error = "スレ要約を作成できませんでした。再試行できます。")) }
    }
}

private suspend fun <T> aiAttempt(timeout: Long = 45_000L, block: suspend () -> T): Result<T> = try {
    Result.success(withTimeout(timeout) { block() })
} catch (e: TimeoutCancellationException) {
    currentCoroutineContext().ensureActive()
    Result.failure(e)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

internal fun CompatThreadAiState.resolveVisibility(
    ownPostNos: Set<String>,
    automaticallyHide: Boolean,
    copyPasteResults: List<PostModerationResult> = if (moderationEnabled) detectCopyPastePosts(posts) else emptyList()
): CompatThreadAiState {
    val candidates = resolveAiHiddenPostState(
        buildAiHiddenPostResolutionContext(posts, ownPostNos),
        results + copyPasteResults
    ).postIds
    return copy(candidatePostNos = candidates, hiddenPostNos = if (automaticallyHide) candidates else emptySet())
}

@Composable
internal fun rememberCompatThreadAi(
    stateStore: AppStateStore?, snapshot: CompatThreadSnapshot?, title: String,
    ownPostNos: Set<String>, retry: Int, listState: LazyListState, displayedPosts: List<CompatPostSnapshot>
): CompatThreadAiState {
    val summaryEnabled = stateStore?.isThreadSummaryModeEnabled?.collectAsState(false)?.value ?: false
    val moderationEnabled = stateStore?.isAiPostFilterEnabled?.collectAsState(false)?.value ?: false
    if (!summaryEnabled && !moderationEnabled) return CompatThreadAiState()
    val platformContext = LocalPlatformContext.current
    val service = rememberSelectedAiService(platformContext)
    val session = remember(service) { CompatThreadAiSession(service) }
    LaunchedEffect(session, moderationEnabled) { if (!moderationEnabled) session.clearModerationRetries() }
    // An API key retry in the settings resumes moderation paused by that key's failure.
    val keyRetries = remember(platformContext) { getAiConnectionStore(platformContext).keyRetries.drop(1) }
    // Keyed by thread, not snapshot: a refresh keeps the previous decisions until re-checked,
    // so AI-hidden posts do not flash back into view.
    val threadKey = snapshot?.tabKey
    var moderationPart by remember(threadKey, service, moderationEnabled) {
        mutableStateOf(CompatThreadAiModerationPart(running = moderationEnabled && snapshot != null))
    }
    var summaryPart by remember(threadKey, service, summaryEnabled) {
        mutableStateOf(CompatThreadAiSummaryPart(running = summaryEnabled && snapshot != null))
    }
    val orderedIds = remember(displayedPosts) { displayedPosts.map { it.postNo } }
    val itemKeys = remember(displayedPosts) { displayedPosts.associate { "${it.postNo}:${it.position}" to it.postNo } }
    val nearbyIds = rememberModerationViewport(listState, orderedIds, itemKeys, moderationEnabled)
    // The viewport is followed inside the worker, so scrolling never restarts or cancels a batch.
    // Summary runs beside moderation instead of waiting behind it.
    val sourceKey = remember(snapshot) { snapshot?.let(::buildCompatThreadAiSourceKey) }
    LaunchedEffect(sourceKey, title, service, summaryEnabled, moderationEnabled, retry) {
        val source = snapshot ?: return@LaunchedEffect
        // Each suspended request cancels by request ID; a service-wide stop can hit its successor.
        coroutineScope {
            if (moderationEnabled) launch {
                session.moderate(source, title, snapshotFlow { nearbyIds.value }, retry, keyRetries) { next ->
                    val previous = moderationPart
                    moderationPart = next.copy(
                        results = if (next.running && next.results.isEmpty()) previous.results else next.results)
                }
            }
            if (summaryEnabled) launch {
                session.summarize(source, title) { next ->
                    val previous = summaryPart
                    summaryPart = next.copy(summary = next.summary ?: previous.summary)
                }
            }
        }
    }
    val moderationPosts = moderationPart.posts
    val copyPasteResults by produceState(emptyList<PostModerationResult>(), moderationPosts, moderationEnabled) {
        value = if (moderationEnabled && moderationPosts.isNotEmpty()) {
            withContext(AppDispatchers.parsing) { detectCopyPastePosts(moderationPosts) }
        } else emptyList()
    }
    val merged = remember(moderationPart, summaryPart, summaryEnabled, moderationEnabled) {
        CompatThreadAiState(
            summaryEnabled = summaryEnabled,
            moderationEnabled = moderationEnabled,
            running = moderationPart.running || summaryPart.running,
            summary = summaryPart.summary,
            summaryError = summaryPart.error,
            moderationError = moderationPart.error,
            posts = moderationPart.posts,
            results = moderationPart.results
        )
    }
    val automaticallyHide = service.automaticallyHideModeratedPosts
    // Hidden-post resolution walks every post; the previous value stays shown while it runs.
    val resolved by produceState(
        CompatThreadAiState(summaryEnabled, moderationEnabled), merged, copyPasteResults, ownPostNos, automaticallyHide
    ) {
        value = withContext(AppDispatchers.parsing) {
            merged.resolveVisibility(ownPostNos, automaticallyHide, copyPasteResults)
        }
    }
    // Keyed by configuration, not instance: every tab composes its own service.
    PublishCompatAiPreview(LocalCompatAiPreviewCache.current, service.configurationKey, snapshot, resolved)
    return resolved
}

/**
 * Publishes the thread's AI-hidden posts for previews in other tabs. The update walks every post,
 * so it runs when its inputs change, not on every recomposition of the thread screen.
 */
@Composable
internal fun PublishCompatAiPreview(
    cache: CompatAiPreviewCache?, owner: Any, snapshot: CompatThreadSnapshot?, resolved: CompatThreadAiState
) {
    val decidedPostNos = remember(resolved) { resolved.results.filter { it.isComplete }.mapTo(HashSet()) { it.postId } }
    DisposableEffect(cache, owner, snapshot, resolved.hiddenPostNos, decidedPostNos) {
        if (snapshot != null) cache?.update(owner, snapshot, resolved.hiddenPostNos, decidedPostNos)
        onDispose { }
    }
}
