package com.valoser.futacha.shared.ai

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private val LOCAL_AVAILABILITY_RECHECK = 60.seconds
// Distinct contexts after which a post the device AI could not decide is not inferred again.
private const val LOCAL_UNDECIDED_MAX_CONTEXTS = 3

/** Independent results: a slow/limited cloud must not delay publication of local decisions. */
internal class HybridModerationService(
    private val local: OnDeviceAiService,
    private val cloud: OnDeviceAiService,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val localTimeoutMillis: Long = 40_000L,
    /**
     * Longest time one call waits for the device AI before its own deadline starts: the readiness
     * check plus the queue for [localInferenceLock]. A busy device AI (model download, summary)
     * then leaves this call's device side pending (retried later), and the cloud decisions are returned
     * well inside the callers' 150 s limit instead of the whole judgement timing out.
     */
    private val localWaitMillis: Long = 60_000L
) : OnDeviceAiService {
    override val isExternalService = true
    override val hybridModeration = true
    override val automaticallyHideModeratedPosts get() = cloud.automaticallyHideModeratedPosts
    override val configurationKey get() = "both:${local.configurationKey}:${cloud.configurationKey}"
    // Shared by concurrent calls (several screens): guarded by cacheMutex.
    private val cacheMutex = Mutex()
    private val localCache = linkedMapOf<String, PostModerationResult>()
    // Inputs the device AI answered without a decision (UNCERTAIN/omitted). Not decisions:
    // never cached as KEEP, only remembered so the same input is not re-inferred or left pending
    // forever. Post key -> digests of the contexts it was undecided in, unlike [localCache]: a
    // post that lacked context is judged again once its own context (title, first post, preceding
    // posts; [ModerationPostContext]) changes, at most [LOCAL_UNDECIDED_MAX_CONTEXTS] times.
    private val localUndecided = linkedMapOf<String, MutableSet<String>>()
    private var localAvailable: Boolean? = null
    private var localCheckedAt: TimeMark? = null
    // The last readiness check did not answer in time (device AI busy): readiness is unknown.
    private var localCheckTimedOut = false
    /**
     * Held while the device AI classifies. A summary on the same device AI takes it too
     * ([RoutedAiService.localInferenceLock]), so neither request's deadline runs while it waits.
     */
    internal val localInferenceLock = Mutex()
    internal fun usesLocal(service: OnDeviceAiService) = local === service

    override suspend fun getAvailability(): AiAvailability {
        val external = cloud.getAvailability()
        // A working cloud can start immediately; local readiness is checked in
        // its own classification job without blocking the independent provider.
        if (!external.isAvailable) refreshLocalAvailabilityIfStale(localWaitMillis)
        val cloudReady = external.isAvailable && external.supportsPostModeration
        return AiAvailability(localAvailable == true || cloudReady,
            supportsPostModeration = localAvailable == true || cloudReady,
            providerLabel = if (localAvailable != false) "端末内AI ＋ OpenAI" else "OpenAI（端末内AIは利用不可）",
            isExternalService = true, externalModeration = true)
    }
    override suspend fun summarizeThread(input: ThreadSummaryInput) = local.summarizeThread(input)
    override suspend fun classifyPosts(input: PostModerationInput) = classifyPosts(input) {}

    /**
     * The device AI may become usable later (download finished); re-check it at a bounded rate.
     * A check that does not answer within [waitMillis] (the device AI is busy) leaves readiness
     * unknown ([localCheckTimedOut]) until the next re-check: like a busy queue, the device side
     * of a judgement then stays pending instead of being reported as unavailable.
     */
    private suspend fun refreshLocalAvailabilityIfStale(waitMillis: Long) {
        val checkedAt = localCheckedAt
        if (localAvailable == true && checkedAt != null) return
        if (checkedAt != null && checkedAt.elapsedNow() < LOCAL_AVAILABILITY_RECHECK) return
        val answer = try {
            withTimeoutOrNull(waitMillis.coerceAtLeast(1L)) { local.getAvailability() }
                ?.let { it.isAvailable && it.supportsPostModeration }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
        localCheckTimedOut = answer == null
        if (answer != null) localAvailable = answer
        localCheckedAt = timeSource.markNow()
    }

    override suspend fun classifyPosts(input: PostModerationInput, onPartialResult: (List<PostModerationResult>) -> Unit): Result<List<PostModerationResult>> = coroutineScope {
        var useLocal = true
        val mutex = Mutex()
        val deviceResults = mutableMapOf<String, PostModerationResult>()
        val deviceUndecided = mutableSetOf<String>()
        val cloudResults = mutableMapOf<String, PostModerationResult>()
        val errors = mutableListOf<Throwable>()
        fun merged(): List<PostModerationResult> = input.posts.mapNotNull { post ->
            val a = deviceResults[post.id]
            val b = cloudResults[post.id]
            if (a == null && b == null) return@mapNotNull null
            val reasons = listOfNotNull(
                a?.takeIf { it.shouldHide }?.let { "端末AI: ${it.reason.orEmpty()}" },
                b?.takeIf { it.shouldHide }?.let { it.reason ?: "OpenAI: 判定閾値以上" }
            )
            PostModerationResult(post.id, reasons.isNotEmpty(), reasons.takeIf { it.isNotEmpty() }?.joinToString(" / "),
                confidence = maxOf(a?.confidence ?: 0f, b?.confidence ?: 0f),
                isComplete = (a != null || post.id in deviceUndecided || !useLocal) && b != null)
        }
        // Called with `mutex` held; a cancelled call must not publish late results.
        fun publish() {
            ensureActive()
            onPartialResult(merged())
        }
        suspend fun attempt(timeout: Long, action: suspend () -> List<PostModerationResult>): Result<List<PostModerationResult>> = try {
            val value = withTimeoutOrNull(timeout) { action() } ?: error("AI判定がタイムアウトしました。")
            Result.success(value)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
        val localJob = launch {
            val waitStarted = timeSource.markNow()
            refreshLocalAvailabilityIfStale(localWaitMillis)
            if (localCheckTimedOut) {
                // Readiness unknown (busy device AI): the device side stays pending and is retried.
                mutex.withLock {
                    errors += IllegalStateException("端末AIが他の処理中のため、今回は判定できませんでした。")
                    publish()
                }
                return@launch
            }
            useLocal = localAvailable == true
            if (!useLocal) { mutex.withLock { publish() }; return@launch }
            fun key(id: String, body: String) = aiDigest(input.threadId + "\n" + id + "\n" + body)
            val keys = input.posts.associate { it.id to key(it.id, it.messageHtml) }
            // Per-post context when the caller provides it, so other posts in the batch do not
            // change the key; otherwise the batch context.
            val postContext = currentCoroutineContext()[ModerationPostContext]?.context
            val batchContextDigest by lazy { aiDigest(input.contextText) }
            val contextDigests = input.posts.associate { post ->
                post.id to (postContext?.let { aiDigest(it.forPosts(listOf(post))) } ?: batchContextDigest)
            }
            fun isUndecided(id: String) = localUndecided[keys.getValue(id)]
                ?.let { contextDigests.getValue(id) in it || it.size >= LOCAL_UNDECIDED_MAX_CONTEXTS } == true
            val pending = mutex.withLock {
                val pending = cacheMutex.withLock {
                    input.posts.filter { post ->
                        val postKey = keys.getValue(post.id)
                        val cached = localCache[postKey]
                        if (cached != null) deviceResults[post.id] = cached
                        val undecided = cached == null && isUndecided(post.id)
                        if (undecided) deviceUndecided += post.id
                        cached == null && !undecided
                    }
                }
                if (deviceResults.isNotEmpty() || deviceUndecided.isNotEmpty()) publish()
                pending
            }
            // The deadline starts once the device AI is ours, not while a summary still runs on it;
            // the wait for it shares the bounded budget with the readiness check above.
            val result = if (pending.isEmpty()) Result.success(emptyList()) else {
                val waitLeft = localWaitMillis - waitStarted.elapsedNow().inWholeMilliseconds
                localInferenceLock.withLockWithin(waitLeft) {
                    attempt(localTimeoutMillis) { local.classifyPosts(input.copy(posts = pending)).getOrThrow() }
                } ?: Result.failure(IllegalStateException("端末AIが他の処理中のため、今回は判定できませんでした。"))
            }
            ensureActive()
            mutex.withLock {
                result.onSuccess { decisions ->
                    val decided = decisions.mapNotNull { decision ->
                        input.posts.firstOrNull { it.id == decision.postId }?.let { it to decision }
                    }
                    decided.forEach { (post, decision) -> deviceResults[post.id] = decision }
                    val undecided = pending.filter { post -> decided.none { it.first.id == post.id } }
                    deviceUndecided += undecided.map { it.id }
                    cacheMutex.withLock {
                        decided.forEach { (post, decision) -> localCache[keys.getValue(post.id)] = decision }
                        undecided.forEach { post ->
                            val postKey = keys.getValue(post.id)
                            val contexts = localUndecided.remove(postKey) ?: linkedSetOf()
                            contexts += contextDigests.getValue(post.id)
                            localUndecided[postKey] = contexts // Most recent last.
                        }
                        while (localCache.size > 2_050) localCache.remove(localCache.keys.first())
                        while (localUndecided.size > 2_050) localUndecided.remove(localUndecided.keys.first())
                    }
                }.onFailure { errors += it }
                publish()
            }
        }
        val cloudJob = launch {
            val result = attempt(120_000L) { cloud.classifyPosts(input).getOrThrow() }
            ensureActive()
            mutex.withLock {
                result.onSuccess { decisions -> decisions.forEach { cloudResults[it.postId] = it } }.onFailure { errors += it }
                publish()
            }
        }
        joinAll(localJob, cloudJob)
        val results = mutex.withLock { merged() }
        if (results.isEmpty() && errors.isNotEmpty()) Result.failure(errors.first()) else Result.success(results)
    }
    override fun cancelActiveRequests() { local.cancelActiveRequests(); cloud.cancelActiveRequests() }
    override fun close() { local.close(); cloud.close() }
}
