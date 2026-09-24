package com.valoser.futacha.shared.media.prompt

import com.valoser.futacha.shared.media.MediaFeature
import com.valoser.futacha.shared.media.MediaFeatureGate
import com.valoser.futacha.shared.media.MediaFeaturePermit
import com.valoser.futacha.shared.media.source.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** Display owns downloads. Metadata retains the very same immutable original revision. */
class PromptMediaSource(
    private val source: OriginalMediaSource,
    private val gate: MediaFeatureGate,
    private val readLocalMetadata: (suspend (String) -> GenerationMetadata)? = null,
    private val parseTimeoutMillis: Long = 2_000L,
    /** A parse that ran out of time is retried after this, not remembered for good. */
    private val budgetRetryAfterMillis: Long = 30_000L,
    private val nowMillis: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
    // Last so callers can pass it as a trailing lambda.
    private val readMetadata: suspend (OriginalMediaStore.Lease) -> GenerationMetadata = {
        MediaGenerationMetadataReader().read(it.info.sizeBytes, it::readAt)
    }
) : OriginalMediaSource by source, AutoCloseable {
    private data class Known(val request: OriginalMediaRequest, val identity: String)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val parsing = Semaphore(2)
    private val known = LinkedHashMap<String, Known>()
    private val aliases = LinkedHashMap<String, String>()
    private data class ParseTask(val permit: MediaFeaturePermit, val job: Job)
    private data class MetadataRead(val read: suspend () -> GenerationMetadata, val close: () -> Unit)
    private val jobs = mutableMapOf<String, ParseTask>()
    private val results = LinkedHashMap<String, GenerationMetadata>()
    // identity -> time after which a timed-out parse may run again
    private val budgetExceeded = LinkedHashMap<String, Long>()
    private val revision = MutableStateFlow(0L)
    val changes: StateFlow<Long> = revision.asStateFlow()
    private var epoch = 0L
    private var sourceEpoch = 0L
    private var clearsInProgress = 0

    init {
        scope.launch {
            gate.permits(MediaFeature.PROMPT).collect {
                // An initial/queued OFF emission must not cancel work started after a new ON.
                if (gate.permit(MediaFeature.PROMPT) == null) invalidate()
            }
        }
    }

    override suspend fun acquire(request: OriginalMediaRequest): OriginalMediaStore.Lease {
        val startedIn = mutex.withLock { sourceEpoch }
        return accept(request, source.acquire(request), startedIn)
    }

    override suspend fun acquireForPlayback(request: OriginalMediaRequest): OriginalMediaPlayback {
        val startedIn = mutex.withLock { sourceEpoch }
        fun wrap(playback: OriginalMediaPlayback): OriginalMediaPlayback = object : OriginalMediaPlayback by playback {
            override fun retain(): OriginalMediaPlayback = wrap(playback.retain())
            override suspend fun complete(): OriginalMediaStore.Lease = accept(request, playback.complete(), startedIn)
        }
        return wrap(source.acquireForPlayback(request))
    }

    private suspend fun accept(request: OriginalMediaRequest, lease: OriginalMediaStore.Lease, startedIn: Long): OriginalMediaStore.Lease {
        try {
            val accepted = mutex.withLock {
                // OFF invalidates semantic jobs, not the download a visible player owns.
                // A completion after OFF→ON must be parsed with the new permission.
                if (startedIn != sourceEpoch || clearsInProgress > 0) return@withLock false
                // A cache probe can have read the old pointer just before a display refresh
                // publishes a newer revision. Never overwrite that display's identity.
                val previous = known[request.url]
                if (!request.allowNetwork && previous != null && previous.identity != lease.identity) return@withLock false
                known.remove(request.url)
                known[request.url] = Known(request.copy(headers = request.headers.toMap()), lease.identity)
                while (known.size > 512) known.remove(known.keys.first())
                // Every watcher re-queries on a change; re-acquiring the same original
                // (scrolling, a second viewer) changes nothing they can see.
                if (previous?.identity != lease.identity) revision.value++
                true
            }
            if (accepted) parse(lease, startedIn)
            return lease
        } catch (failure: Throwable) {
            lease.close()
            throw failure
        }
    }

    private suspend fun parse(original: OriginalMediaStore.Lease, startedIn: Long) {
        parseIdentity(original.identity, startedIn) {
            val lease = original.retain()
            MetadataRead({ readMetadata(lease) }, lease::close)
        }
    }

    private suspend fun parseIdentity(identity: String, startedIn: Long, refresh: Boolean = false, prepare: () -> MetadataRead) {
        val permit = gate.permit(MediaFeature.PROMPT) ?: return
        mutex.withLock {
            if (startedIn != sourceEpoch || clearsInProgress > 0) return
            val coolingDown = (budgetExceeded[identity] ?: Long.MIN_VALUE) > nowMillis()
            if (!gate.isCurrent(permit) || (!refresh && (results.containsKey(identity) || coolingDown))) return
            jobs[identity]?.let { previous ->
                if (gate.isCurrent(previous.permit)) return
                previous.job.cancel()
            }
            if (refresh) { results.remove(identity); budgetExceeded.remove(identity); revision.value++ }
            val input = prepare()
            val generation = epoch
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val result = parsing.withPermit {
                        if (!gate.isCurrent(permit)) return@launch
                        try {
                            withTimeoutOrNull(parseTimeoutMillis) { input.read() }
                                ?: GenerationMetadata(coverage = MetadataCoverage.BUDGET_EXCEEDED)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { GenerationMetadata(coverage = MetadataCoverage.SOURCE_UNAVAILABLE) }
                    }
                    mutex.withLock {
                        if (generation == epoch && gate.isCurrent(permit) &&
                            result.coverage == MetadataCoverage.BUDGET_EXCEEDED
                        ) {
                            // A busy device must not hide the prompt of this original for good.
                            results.remove(identity)
                            budgetExceeded.remove(identity)
                            budgetExceeded[identity] = nowMillis() + budgetRetryAfterMillis
                            while (budgetExceeded.size > 128) budgetExceeded.remove(budgetExceeded.keys.first())
                            revision.value++
                        } else if (generation == epoch && gate.isCurrent(permit)) {
                            budgetExceeded.remove(identity)
                            results[identity] = result
                            // Conservative accounting includes candidate strings referenced more than once.
                            fun weight() = results.values.sumOf { metadata -> metadata.candidates.sumOf {
                                2L * (it.raw.length + (it.positive?.length ?: 0) + (it.negative?.length ?: 0) + (it.settings?.length ?: 0))
                            } }
                            while (results.size > 128 || weight() > 4L * 1024 * 1024) results.remove(results.keys.first())
                            revision.value++
                        }
                    }
                } finally {
                    input.close()
                    val ownJob = currentCoroutineContext()[Job]
                    withContext(NonCancellable) {
                        mutex.withLock { if (jobs[identity]?.job === ownJob) jobs.remove(identity) }
                    }
                }
            }
            // Also release when a LAZY coroutine is cancelled before entering its body.
            job.invokeOnCompletion { input.close() }
            jobs[identity] = ParseTask(permit, job)
            job.start()
        }
    }

    /** Late enable/memory-cache hits: inspect available disk bytes, never issue an HTTP request. */
    suspend fun inspectCached(url: String) {
        if (gate.permit(MediaFeature.PROMPT) == null) return
        if (isLocalPromptMediaUrl(url)) {
            val reader = readLocalMetadata ?: return
            val identity = "local:$url"
            val startedIn = mutex.withLock {
                if (clearsInProgress > 0) return
                known.remove(url)
                known[url] = Known(OriginalMediaRequest(url, allowNetwork = false), identity)
                while (known.size > 512) known.remove(known.keys.first())
                sourceEpoch
            }
            // A saved file may have been replaced between visits. Coalesce active reads,
            // but refresh metadata when reopened instead of retaining stale local results.
            parseIdentity(identity, startedIn, refresh = true) { MetadataRead({ reader(url) }, {}) }
            return
        }
        val request = mutex.withLock {
            val actual = aliases[url] ?: url
            known[actual]?.request ?: OriginalMediaRequest(actual)
        }.copy(allowNetwork = false, reloadToken = 0)
        try {
            val lease = acquire(request)
            lease.close()
        } catch (failure: CancellationException) { throw failure }
        catch (_: Exception) { /* Missing originals stay unknown; display can populate them later. */ }
    }

    suspend fun bindSuccessfulUrl(requested: String, actual: String) {
        if (requested == actual) return
        mutex.withLock {
            aliases.remove(requested)
            aliases[requested] = actual
            while (aliases.size > 512) aliases.remove(aliases.keys.first())
            revision.value++
        }
    }

    suspend fun metadata(url: String): GenerationMetadata? = mutex.withLock {
        if (gate.permit(MediaFeature.PROMPT) == null) null
        else known[aliases[url] ?: url]?.identity?.let { identity ->
            results[identity] ?: budgetExceeded[identity]?.takeIf { it > nowMillis() }
                ?.let { GenerationMetadata(coverage = MetadataCoverage.BUDGET_EXCEEDED) }
        }
    }

    private suspend fun invalidate() = mutex.withLock {
        epoch++
        jobs.values.forEach { it.job.cancel() }
        jobs.clear()
        results.clear()
        budgetExceeded.clear()
        revision.value++
    }

    override suspend fun clear() {
        mutex.withLock { sourceEpoch++; clearsInProgress++ }
        try {
            invalidate()
            mutex.withLock { known.clear(); aliases.clear(); revision.value++ }
            source.clear()
        } finally {
            withContext(NonCancellable) {
                // An acquire joining while disk clear is suspended must not re-register
                // the old cache after clear returns, even if its player keeps a pin.
                mutex.withLock { sourceEpoch++; clearsInProgress--; revision.value++ }
            }
        }
    }

    override fun close() { scope.cancel() }
}
