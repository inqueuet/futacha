package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import com.valoser.futacha.shared.ai.PostModerationInput
import com.valoser.futacha.shared.model.Post
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Use item identities, not list indices: headers, NG filtering and tree order change indices. */
internal fun moderationNearbyPostIds(
    orderedIds: List<String>, visibleIds: Set<String>, radius: Int = 8
): Set<String> {
    val selected = linkedSetOf<String>()
    orderedIds.forEachIndexed { index, id ->
        if (id in visibleIds) {
            for (nearby in (index - radius).coerceAtLeast(0)..(index + radius).coerceAtMost(orderedIds.lastIndex)) {
                selected += orderedIds[nearby]
            }
        }
    }
    return selected
}

/**
 * Returns the posts around the viewport as a State so that AI workers can follow it without
 * restarting. The last value is kept while the displayed list changes (for example when AI
 * hiding removes rows), instead of dropping to an empty set and back.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun rememberModerationViewport(
    listState: LazyListState,
    orderedIds: List<String>,
    idsByItemKey: Map<String, String>,
    enabled: Boolean
): State<Set<String>> {
    val target = remember(listState) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(listState, orderedIds, idsByItemKey, enabled) {
        if (!enabled) {
            target.value = emptySet()
            return@LaunchedEffect
        }
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo.mapNotNull { idsByItemKey[it.key] }.toSet()
            moderationNearbyPostIds(orderedIds, visible)
        }.distinctUntilChanged().debounce(250).collect { target.value = it }
    }
    return target
}

private data class ModerationViewportValue(val ids: Set<String>?)

/**
 * Per-screen count of answers without a decision (UNCERTAIN or omitted), for the device AI only.
 * Such answers are never stored as decisions, so without a bound the same unchanged input would
 * be re-inferred whenever the viewport moved. Hosts [record] each undecided answer; an input is
 * then retried until it has [maxAttempts] of them, and the retry delay doubles after each one.
 * Failures and cancellations are not recorded and stay retryable. Keys must change with the
 * inference input (body, context, provider) so edited posts are tried again.
 */
internal class ModerationRetryBudget(
    private val maxAttempts: Int = 3,
    private val maxEntries: Int = 4_096
) {
    private val attempts = linkedMapOf<Any, Int>()
    fun attempts(key: Any): Int = attempts[key] ?: 0
    fun allows(key: Any): Boolean = attempts(key) < maxAttempts
    fun record(key: Any) {
        attempts[key] = (attempts.remove(key) ?: 0) + 1
        while (attempts.size > maxEntries) attempts.remove(attempts.keys.first())
    }
    fun clear() = attempts.clear()
}

/**
 * Drives viewport moderation from one long-lived coroutine. Viewport changes only change which
 * posts are queued next; a batch already sent is never cancelled because the user scrolled.
 *
 * [nextBatch] receives the latest nearby ids (`null` = every post) and the ids already attempted
 * for that viewport, and returns the next batch or `null` when nothing is pending. Attempts are
 * reset when the viewport changes, so failed or incomplete posts are retried after moving away and
 * back. [runBatch] returns false to pause (for example on external API failure) until the viewport
 * changes. [retryDelayMillis] bounds repeat inference across viewport changes. [startDelayMillis] is applied once, before the first batch of this run.
 * With [retryBudget], inputs ([retryKey]) left undecided are retried with growing delays, and
 * exhausted posts are passed to [nextBatch] as attempted for the rest of this run.
 * Each value of [resume] (an explicit API key retry) is handled like a viewport change: a paused
 * worker continues and failed posts are retried without waiting for the user to scroll.
 *
 * Returns when [viewport] completes and nothing is pending; a UI snapshotFlow never completes,
 * so the worker then lives until its scope is cancelled.
 */
internal suspend fun runViewportModeration(
    viewport: Flow<Set<String>?>,
    startDelayMillis: Long = 0L,
    batchDelayMillis: Long = 0L,
    retryDelayMillis: Long = 0L,
    onViewportChanged: () -> Unit = {},
    resume: Flow<*> = emptyFlow<Unit>(),
    retryBudget: ModerationRetryBudget? = null,
    retryKey: (Post) -> Any = { it.id },
    nextBatch: suspend (nearby: Set<String>?, attempted: Set<String>) -> PostModerationInput?,
    runBatch: suspend (PostModerationInput) -> Boolean
) = coroutineScope {
    val latest = MutableStateFlow<ModerationViewportValue?>(null)
    val viewportDone = MutableStateFlow(false)
    val resumes = MutableStateFlow(0)
    val collector = launch {
        viewport.collect { latest.value = ModerationViewportValue(it) }
        viewportDone.value = true
    }
    val resumeCollector = launch { resume.collect { resumes.value += 1 } }
    try {
        var startDelayPending = startDelayMillis > 0L
        var current: ModerationViewportValue? = null
        var handledResumes = 0
        val attempted = mutableSetOf<String>()
        val lastAttempts = linkedMapOf<String, TimeMark>()
        val exhausted = mutableSetOf<String>() // Not reset by viewport changes.
        var paused = false
        while (true) {
            val next = combine(latest, viewportDone) { value, done -> value to done }
                .first { (value, done) -> value != null || done }.first ?: return@coroutineScope
            if (next != current || resumes.value != handledResumes) {
                current = next
                handledResumes = resumes.value
                attempted.clear()
                paused = false
                onViewportChanged()
            }
            val batch = if (paused) null else nextBatch(next.ids, attempted + exhausted)?.takeIf { it.posts.isNotEmpty() }
            if (batch == null) {
                combine(latest, viewportDone, resumes) { value, done, resumed ->
                    value != next || done || resumed != handledResumes
                }.first { it }
                if (latest.value == next && viewportDone.value) return@coroutineScope
                continue
            }
            if (startDelayPending) {
                startDelayPending = false
                delay(startDelayMillis)
                continue
            }
            if (retryBudget != null) {
                val spent = batch.posts.filter { !retryBudget.allows(retryKey(it)) }.map { it.id }
                if (spent.isNotEmpty()) {
                    exhausted += spent
                    continue // Ask for a batch without them.
                }
            }
            val wait = batch.posts.maxOfOrNull { post ->
                lastAttempts[post.id]?.let { it.retryWait(retryDelayMillis, retryBudget?.attempts(retryKey(post)) ?: 1) } ?: 0L
            } ?: 0L
            if (wait > 0L) {
                delay(wait)
                continue // Re-read the viewport after the cooldown.
            }
            val now = TimeSource.Monotonic.markNow()
            batch.posts.forEach { lastAttempts[it.id] = now }
            while (lastAttempts.size > 8_192) lastAttempts.remove(lastAttempts.keys.first())
            attempted += batch.posts.map { it.id }
            if (!runBatch(batch)) paused = true
            if (batchDelayMillis > 0L) delay(batchDelayMillis)
        }
    } finally {
        collector.cancel()
        resumeCollector.cancel()
    }
}

/** Remaining cooldown after [attempts] sends: the base delay doubles per earlier attempt. */
private fun TimeMark.retryWait(baseDelayMillis: Long, attempts: Int): Long =
    (baseDelayMillis shl (attempts - 1).coerceIn(0, 6)) - elapsedNow().inWholeMilliseconds
