package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.ui.ftutter.FTUTTER_NG_REGEX_MATCH_LIMIT
import com.valoser.futacha.shared.ui.ftutter.FTUTTER_NG_REGEX_STEP_LIMIT
import com.valoser.futacha.shared.ui.ftutter.FtutterDeadlineCharSequence
import com.valoser.futacha.shared.ui.ftutter.ftutterIsSlowNgPattern
import com.valoser.futacha.shared.ui.ftutter.ftutterMarkSlowNgPattern
import com.valoser.futacha.shared.ui.ftutter.ftutterRegexIsSafe
import com.valoser.futacha.shared.ui.ftutter.ftutterRegexIsTooSlow
import kotlin.coroutines.cancellation.CancellationException
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * FutabaViewer 18-20's regular-expression NG and watch words for としあき(仮).
 *
 * The pattern is used as written: no options, so full/half width and upper/lower case are
 * told apart ("正規表現では上の2つを区別します"), and it is searched for anywhere in the text.
 * A pattern that cannot be read is skipped; a pattern found to be slow (the same probe and
 * time limit as ふたったー風) is not used, so a bad rule never freezes the screen.
 */
const val COMPAT_NG_REGEX_MAX_CHARS = 100
const val COMPAT_NG_REGEX_INVALID_MESSAGE = "正規表現として解釈できません"
const val COMPAT_NG_REGEX_LABEL = "正規表現"

/** 18-20 shows a regular-expression rule as 「［正規表現］語」. */
fun compatNgRuleDisplayWord(word: String, isRegex: Boolean): String =
    if (isRegex) "［正規表現］$word" else word

/** The compiled pattern, or null when it cannot be read. */
fun compatNgRegexOrNull(pattern: String): Regex? {
    if (pattern.isEmpty()) return null
    return try {
        Regex(pattern)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }
}

/** What the add/edit dialog says about [pattern]; null when it can be saved. */
fun compatNgRegexInputError(pattern: String): String? = when {
    pattern.length > COMPAT_NG_REGEX_MAX_CHARS -> "正規表現は${COMPAT_NG_REGEX_MAX_CHARS}文字までです"
    compatNgRegexOrNull(pattern) == null -> COMPAT_NG_REGEX_INVALID_MESSAGE
    else -> null
}

/**
 * The longest part of a text a regular expression is matched against. A post longer than this is
 * matched on its beginning only: Android cannot stop a running match, so the pattern is probed up
 * to this length before it meets a long text ([compatRegexIsSafeForLongText]).
 */
const val COMPAT_NG_REGEX_MAX_TEXT_CHARS = 4096

/** Texts up to this length are covered by the shared short probe ([ftutterRegexIsSafe]). */
private const val COMPAT_NG_REGEX_SHORT_PROBE_CHARS = 256

/** One regular-expression rule ready for matching. */
class CompatNgRegexWord(val pattern: String, val regex: Regex) {
    override fun equals(other: Any?): Boolean = other is CompatNgRegexWord && other.pattern == pattern
    override fun hashCode(): Int = pattern.hashCode()
    override fun toString(): String = "CompatNgRegexWord($pattern)"

    /** True when the pattern is found in [text] within the time limit. */
    fun matches(text: String): Boolean = search(text) { regex.containsMatchIn(it) } == true

    /** The part of [text] the pattern found, or null (also for a slow pattern). */
    fun find(text: String): String? = search(text) { regex.find(it)?.value }

    private inline fun <T> search(text: String, match: (CharSequence) -> T): T? {
        if (ftutterIsSlowNgPattern(pattern)) return null
        // Android cannot stop a running match, so every pattern is probed once before use.
        if (!ftutterRegexIsSafe(pattern, regex)) return null
        val target = if (text.length > COMPAT_NG_REGEX_MAX_TEXT_CHARS) text.take(COMPAT_NG_REGEX_MAX_TEXT_CHARS) else text
        if (target.length > COMPAT_NG_REGEX_SHORT_PROBE_CHARS && !compatRegexIsSafeForLongText(pattern, regex)) return null
        val started = TimeSource.Monotonic.markNow()
        return try {
            match(FtutterDeadlineCharSequence(target, started + FTUTTER_NG_REGEX_MATCH_LIMIT)).also {
                // A match that ran over the limit without being stopped (Android) is not tried again.
                if (started.elapsedNow() > FTUTTER_NG_REGEX_MATCH_LIMIT) ftutterMarkSlowNgPattern(pattern)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            ftutterMarkSlowNgPattern(pattern)
            null
        }
    }
}

/**
 * Probe lengths past the short probe, doubling up to [COMPAT_NG_REGEX_MAX_TEXT_CHARS]. Each step may take
 * the short step's time scaled by the square of the length (an ordinary `a+b` is quadratic), but never
 * more than one match's limit, so a cubic or worse pattern is refused before it meets a long post.
 */
internal val COMPAT_NG_REGEX_LONG_PROBE_LENGTHS: List<Int> =
    generateSequence(COMPAT_NG_REGEX_SHORT_PROBE_CHARS * 2) { it * 2 }.takeWhile { it <= COMPAT_NG_REGEX_MAX_TEXT_CHARS }.toList()

internal fun compatLongProbeStepLimit(length: Int): Duration {
    val scale = length.toDouble() / COMPAT_NG_REGEX_SHORT_PROBE_CHARS
    return minOf(FTUTTER_NG_REGEX_STEP_LIMIT * (scale * scale), FTUTTER_NG_REGEX_MATCH_LIMIT)
}

@OptIn(ExperimentalAtomicApi::class)
private val compatLongTextVerdicts = AtomicReference<Map<String, Boolean>>(emptyMap())

/** Whether [pattern] stays fast on texts up to [COMPAT_NG_REGEX_MAX_TEXT_CHARS] (probed once per pattern). */
@OptIn(ExperimentalAtomicApi::class)
internal fun compatRegexIsSafeForLongText(pattern: String, regex: Regex): Boolean {
    compatLongTextVerdicts.load()[pattern]?.let { return it }
    val safe = !ftutterRegexIsTooSlow(
        regex, pattern,
        lengths = COMPAT_NG_REGEX_LONG_PROBE_LENGTHS,
        stepLimitFor = ::compatLongProbeStepLimit
    )
    while (true) {
        val current = compatLongTextVerdicts.load()
        if (current.containsKey(pattern) || compatLongTextVerdicts.compareAndSet(current, current + (pattern to safe))) break
    }
    if (!safe) ftutterMarkSlowNgPattern(pattern)
    return safe
}

private const val COMPAT_NG_REGEX_CACHE_SIZE = 256

@OptIn(ExperimentalAtomicApi::class)
private val compatNgRegexCache = AtomicReference<Map<String, CompatNgRegexWord?>>(emptyMap())

/** The compiled rule for [pattern], made once (a catalog asks for every cell), or null when it cannot be read. */
@OptIn(ExperimentalAtomicApi::class)
fun compatNgRegexWordOrNull(pattern: String): CompatNgRegexWord? {
    val cached = compatNgRegexCache.load()
    if (cached.containsKey(pattern)) return cached[pattern]
    val word = compatNgRegexOrNull(pattern)?.let { CompatNgRegexWord(pattern, it) }
    while (true) {
        val current = compatNgRegexCache.load()
        val next = (if (current.size >= COMPAT_NG_REGEX_CACHE_SIZE) emptyMap() else current) + (pattern to word)
        if (compatNgRegexCache.compareAndSet(current, next)) break
    }
    return word
}

/** The regular-expression rules among [rules] that can be read, once per pattern. */
fun compatNgRegexWords(rules: Sequence<CompatNgRule>): List<CompatNgRegexWord> =
    rules.filter(CompatNgRule::isRegex)
        .map(CompatNgRule::normalizedValue)
        .distinct()
        .mapNotNull(::compatNgRegexWordOrNull)
        .toList()

/**
 * Runs the one-time probes of [words] now. Call it off the main thread before the words meet the
 * screen (a catalog cell asks for its watch words while it is drawn).
 */
fun compatWarmNgRegexWords(words: List<CompatNgRegexWord>) {
    words.forEach { word ->
        if (ftutterRegexIsSafe(word.pattern, word.regex)) compatRegexIsSafeForLongText(word.pattern, word.regex)
    }
}
