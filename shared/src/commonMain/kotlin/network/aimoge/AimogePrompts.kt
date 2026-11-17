package com.valoser.futacha.shared.network.aimoge

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What a write asks the person at the moment of the tap. */
sealed interface AimogePromptRequest {
    /** May the あいもげ delete key be sent with this kind of write (J7)? A key is made when none is stored. */
    data object DeleteKeyConsent : AimogePromptRequest
    /** The app is not approved yet: offer the approval (the draft stays on screen). */
    data object Approval : AimogePromptRequest
    /** Ask before sending (an unconfirmed post under another approval and the like). */
    data class Confirm(val message: String) : AimogePromptRequest
}

/**
 * The questions a write asks while it waits (AM-B10). One host per mode root ([com.valoser.futacha.shared.ui.aimoge.AimogeWritePromptHost])
 * shows [current] and answers it. With no host on screen (tests, the background) every question is answered "no",
 * so nothing is sent without the person.
 */
object AimogePrompts {
    private val _current = MutableStateFlow<Pair<AimogePromptRequest, CompletableDeferred<Boolean>>?>(null)
    val current: StateFlow<Pair<AimogePromptRequest, CompletableDeferred<Boolean>>?> = _current.asStateFlow()
    private val one = Mutex()
    // Hosts attach and detach from the main thread today; the count is atomic so another caller cannot break it.
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    private val hostCount = kotlin.concurrent.atomics.AtomicInt(0)
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    private val hosts: Int get() = hostCount.load()

    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    fun attachHost() { hostCount.addAndFetch(1) }

    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    fun detachHost() {
        while (true) {
            val old = hostCount.load()
            val next = (old - 1).coerceAtLeast(0)
            if (hostCount.compareAndSet(old, next)) {
                if (next == 0) answerOpenQuestionsNo()
                return
            }
        }
    }

    /**
     * Answers whatever is open with "no" (nothing is sent). The host calls this when the app locks, so a question left
     * under the lock does not hold the other あいもげ writes waiting (audit P2-L3).
     */
    fun answerOpenQuestionsNo() {
        _current.value?.second?.complete(false); _delChoice.value?.complete(null); _choice.value?.second?.complete(null)
    }

    private val _choice = MutableStateFlow<Pair<AimogeChoiceRequest, CompletableDeferred<Int?>>?>(null)
    val choice: StateFlow<Pair<AimogeChoiceRequest, CompletableDeferred<Int?>>?> = _choice.asStateFlow()

    /** One of [AimogeChoiceRequest.options] (its index), or null when cancelled; with no host, [whenNoHost]. */
    suspend fun askChoice(request: AimogeChoiceRequest, whenNoHost: Int?): Int? = one.withLock {
        if (hosts == 0) return whenNoHost
        val answer = CompletableDeferred<Int?>()
        _choice.value = request to answer
        try { answer.await() } finally { _choice.value = null }
    }

    /** What the del button chose on あいもげ: null = cancelled, [AimogeDelChoice.Del] or a report with its reason. */
    private val _delChoice = MutableStateFlow<CompletableDeferred<AimogeDelChoice?>?>(null)
    val delChoice: StateFlow<CompletableDeferred<AimogeDelChoice?>?> = _delChoice.asStateFlow()

    suspend fun askDelOrReport(): AimogeDelChoice? = one.withLock {
        if (hosts == 0) return AimogeDelChoice.Del
        val answer = CompletableDeferred<AimogeDelChoice?>()
        _delChoice.value = answer
        try { answer.await() } finally { _delChoice.value = null }
    }

    suspend fun ask(request: AimogePromptRequest): Boolean = one.withLock {
        if (hosts == 0) return false
        val answer = CompletableDeferred<Boolean>()
        _current.value = request to answer
        try { answer.await() } finally { _current.value = null }
    }
}

/** A choice asked at the tap: a new thread's lifetime, or closing the person's own thread. */
data class AimogeChoiceRequest(
    val tag: String,
    val title: String,
    val text: String,
    val options: List<String>,
    val confirmLabel: String,
    val initial: Int = 0
)

/** The del button on あいもげ offers del (like Futaba) and the site's report (`/report`, with a reason). */
sealed interface AimogeDelChoice {
    data object Del : AimogeDelChoice
    data class Report(val reason: AimogeReportReason, val detail: String) : AimogeDelChoice
}

/**
 * Taps on poll options ([AIMOGE_POLL_LINK_PREFIX]) are answered inside the app. The repository that drew the poll
 * registers [handler]; it returns the message to show.
 */
object AimogePollLinks {
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    private val registered = kotlin.concurrent.atomics.AtomicReference(emptyList<suspend (String, String) -> String>())

    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    private fun change(edit: (List<suspend (String, String) -> String>) -> List<suspend (String, String) -> String>) {
        while (true) {
            val old = registered.load()
            if (registered.compareAndSet(old, edit(old))) return
        }
    }

    /** The newest still-open repository's answer; a closed one never takes the link from another. */
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    var handler: (suspend (postId: String, optionId: String) -> String)?
        get() = registered.load().lastOrNull()
        set(value) = change { listOfNotNull(value) }

    fun register(handler: suspend (String, String) -> String) = change { it + handler }

    /** Removes only [handler] (by identity); the others stay. */
    fun unregister(handler: suspend (String, String) -> String) = change { list ->
        val i = list.indexOfLast { it === handler }
        if (i < 0) list else list.toMutableList().also { it.removeAt(i) }
    }

    /** The post UUID and option id of a poll link, or null for any other address. */
    fun parse(url: String): Pair<String, String>? {
        if (!url.startsWith(AIMOGE_POLL_LINK_PREFIX)) return null
        val parts = url.removePrefix(AIMOGE_POLL_LINK_PREFIX).split('/')
        if (parts.size != 2 || parts.any { it.isBlank() || it.length > 64 }) return null
        return parts[0] to parts[1]
    }
}

/** A short message to show after an in-app あいもげ action (a poll answer); the host shows and clears it. */
object AimogeNotices {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun show(text: String) { _message.value = text }
    fun clear() { _message.value = null }
}

/** Answers an in-app あいもげ link (a poll option); false for every other address, which the launcher opens. */
fun handleAimogeInternalLink(url: String): Boolean {
    val (postId, optionId) = AimogePollLinks.parse(url) ?: return false
    val handler = AimogePollLinks.handler
    if (handler == null) {
        AimogeNotices.show(AIMOGE_POLL_UNAVAILABLE_MESSAGE)
        return true
    }
    AimogeWriteRegistry.appScope.launch {
        val text = try { handler(postId, optionId) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { AIMOGE_POLL_UNAVAILABLE_MESSAGE }
        AimogeNotices.show(text)
    }
    return true
}

const val AIMOGE_POLL_UNAVAILABLE_MESSAGE = "アンケートに答えられませんでした。スレを読み直してからお試しください"
