package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.*
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.ai.*
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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

/** Per-screen caches: no board history, NG rules or source snapshots are modified. */
internal class CompatThreadAiSession(private val service: OnDeviceAiService) {
    private val summaries = linkedMapOf<String, ThreadSummary>()
    private val moderation = linkedMapOf<ThreadPostModerationCacheKey, PostModerationResult>()

    suspend fun analyze(
        snapshot: CompatThreadSnapshot,
        title: String,
        summaryEnabled: Boolean,
        moderationEnabled: Boolean,
        publish: (CompatThreadAiState) -> Unit
    ) {
        if (!summaryEnabled && !moderationEnabled) return
        var state = CompatThreadAiState(summaryEnabled, moderationEnabled, running = true)
        publish(state)
        val availability = aiAttempt { withContext(AppDispatchers.io) { service.getAvailability() } }
            .getOrElse {
                publish(state.copy(running = false,
                    summaryError = if (summaryEnabled) "AIの利用状況を確認できませんでした。" else null,
                    moderationError = if (moderationEnabled) "AIの利用状況を確認できませんでした。" else null))
                return
            }
        val posts = withContext(AppDispatchers.parsing) { snapshot.toThreadPage(snapshot.tabKey).posts }
        state = state.copy(posts = posts)
        if (moderationEnabled) {
            if (!availability.supportsPostModeration) {
                state = state.copy(moderationError = availability.unavailableReason ?: "荒らし判定を利用できません。")
            } else {
                val source = resolveThreadAiPostModerationSourcePosts(posts)
                val results = mutableListOf<PostModerationResult>()
                // OpenAI owns its score cache and reevaluates threshold/category changes.
                val pending = if (service.externalModeration) source else source.filter { post ->
                    val cached = moderation[buildThreadPostModerationCacheKey(snapshot.tabKey, post, service.configurationKey)]
                    if (cached != null) results += cached
                    cached == null
                }
                val batches = if (service.externalModeration) listOf(pending) else pending.chunked(8)
                for (batch in batches.filter { it.isNotEmpty() }) {
                    val result = aiAttempt(if (service.externalModeration) 300_000L else 45_000L) {
                        service.classifyPosts(PostModerationInput(snapshot.tabKey, batch)).getOrThrow()
                    }
                    result.onSuccess { classified ->
                        val byId = classified.associateBy { it.postId }
                        for (post in batch) {
                            // Local models return only HIDE rows. Cache omitted KEEP
                            // decisions too, so unchanged ordinary replies are not rerun.
                            val decision = byId[post.id] ?: if (!service.externalModeration) {
                                PostModerationResult(post.id, shouldHide = false)
                            } else continue
                            results += decision
                            if (!service.externalModeration) putBoundedAiCacheEntry(moderation,
                                buildThreadPostModerationCacheKey(snapshot.tabKey, post, service.configurationKey),
                                decision, THREAD_AI_POST_MODERATION_CACHE_MAX_ENTRIES)
                        }
                    }.onFailure {
                        state = state.copy(moderationError = "一部のレスを判定できませんでした。再試行できます。")
                    }
                    state = state.copy(results = results.toList())
                    publish(state)
                    if (result.isFailure && service.externalModeration) break
                }
                state = state.copy(results = results.toList())
            }
            publish(state)
        }
        if (summaryEnabled) {
            if (!availability.supportsThreadSummary) {
                state = state.copy(summaryError = availability.unavailableReason ?: "この端末ではスレ要約を利用できません。")
            } else {
                val input = ThreadSummaryInput(snapshot.tabKey, title, posts, snapshot.tabKey, snapshot.isTruncated)
                val cacheKey = withContext(AppDispatchers.parsing) {
                    snapshot.tabKey + "\n" + title + "\n" + snapshot.isTruncated + "\n" + buildThreadSummarySourceText(input)
                }
                val cached = summaries[cacheKey]
                val result = if (cached != null) Result.success(cached) else aiAttempt {
                    service.summarizeThread(input).getOrThrow()
                }
                result.onSuccess {
                    putBoundedAiCacheEntry(summaries, cacheKey, it, THREAD_AI_CACHE_MAX_ENTRIES)
                    state = state.copy(summary = it)
                }.onFailure { state = state.copy(summaryError = "スレ要約を作成できませんでした。再試行できます。") }
            }
        }
        publish(state.copy(running = false))
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

internal fun CompatThreadAiState.resolveVisibility(ownPostNos: Set<String>, automaticallyHide: Boolean): CompatThreadAiState {
    val candidates = resolveAiHiddenPostState(
        buildAiHiddenPostResolutionContext(posts, ownPostNos), results
    ).postIds
    return copy(candidatePostNos = candidates, hiddenPostNos = if (automaticallyHide) candidates else emptySet())
}

@Composable
internal fun rememberCompatThreadAi(
    stateStore: AppStateStore?, snapshot: CompatThreadSnapshot?, title: String,
    ownPostNos: Set<String>, retry: Int
): CompatThreadAiState {
    val summaryEnabled = stateStore?.isThreadSummaryModeEnabled?.collectAsState(false)?.value ?: false
    val moderationEnabled = stateStore?.isAiPostFilterEnabled?.collectAsState(false)?.value ?: false
    if (!summaryEnabled && !moderationEnabled) return CompatThreadAiState()
    val service = rememberSelectedAiService(LocalPlatformContext.current)
    val session = remember(service) { CompatThreadAiSession(service) }
    var state by remember(snapshot, service, summaryEnabled, moderationEnabled, retry) {
        mutableStateOf(CompatThreadAiState(summaryEnabled, moderationEnabled, running = snapshot != null))
    }
    LaunchedEffect(snapshot, title, service, summaryEnabled, moderationEnabled, retry) {
        try {
            snapshot?.let { session.analyze(it, title, summaryEnabled, moderationEnabled) { next -> state = next } }
        } finally {
            service.cancelActiveRequests()
        }
    }
    return remember(state, ownPostNos, service) { state.resolveVisibility(ownPostNos, service.automaticallyHideModeratedPosts) }
}
