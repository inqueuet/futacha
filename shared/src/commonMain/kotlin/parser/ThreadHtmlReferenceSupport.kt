package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.util.Logger
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal data class ThreadReferenceBuildConfig(
    val tag: String,
    val maxReferenceBuildTimeMs: Long,
    val maxPartialMatchScanLines: Int,
    val minPartialMatchLength: Int,
    val noReferenceRegex: Regex,
    val leadingNumberRegex: Regex,
    val idReferenceRegex: Regex,
    val whitespaceRegex: Regex,
    val mediaFilenameRegex: Regex
)

internal data class ThreadReferenceData(
    val counts: Map<String, Int>,
    val references: Map<String, List<QuoteReference>>,
    val timedOut: Boolean = false
)

private data class ThreadQuoteLineResolution(
    val targets: Set<String>,
    val isExplicit: Boolean
)

private data class ThreadQuoteSourceContext(
    val candidates: Set<String>,
    val hasRequotedLine: Boolean
)

private data class ThreadLineTargets(
    val plain: MutableSet<String> = mutableSetOf(),
    val quoted: MutableSet<String> = mutableSetOf(),
    val byDepth: MutableMap<Int, MutableSet<String>> = mutableMapOf()
) {
    fun resolve(depth: Int = 0): Set<String> = if (depth == 0) {
        if (plain.isNotEmpty()) plain else quoted
    } else byDepth[depth].orEmpty()
}

internal fun buildThreadReferenceData(
    posts: List<Post>,
    config: ThreadReferenceBuildConfig,
    decodeHtmlEntities: (String) -> String,
    stripTags: (String) -> String
): ThreadReferenceData {
    if (posts.isEmpty()) return ThreadReferenceData(emptyMap(), emptyMap())

    val posterIdIndex = mutableMapOf<String, MutableSet<String>>()
    val messageLineIndex = mutableMapOf<String, ThreadLineTargets>()
    val mediaFileIndex = mutableMapOf<String, MutableSet<String>>()
    val counts = mutableMapOf<String, Int>()
    val references = mutableMapOf<String, MutableList<QuoteReference>>()
    val startedAt = TimeSource.Monotonic.markNow()
    var timedOut = false
    val knownPostIds = posts.mapTo(HashSet(posts.size)) { it.id }
    val earlierPostIds = HashSet<String>(posts.size)

    for ((index, post) in posts.withIndex()) {
        if (index > 0) earlierPostIds += posts[index - 1].id
        if (index % 32 == 0) {
            val elapsed = startedAt.elapsedNow().inWholeMilliseconds
            if (elapsed > config.maxReferenceBuildTimeMs) {
                timedOut = true
                Logger.w(config.tag, "Reference rebuild timed out at post=$index elapsed=${elapsed}ms")
                break
            }
        }
        if (post.messageHtml.isBlank()) continue

        val lines = decodeHtmlEntities(stripTags(post.messageHtml)).lines()
        if (lines.isEmpty()) continue
        val quoteContexts = if (lines.any { threadQuoteDepth(it.trimStart()) > 1 }) {
            buildThreadQuoteSourceContexts(lines, messageLineIndex, config, startedAt)
        } else emptyArray()
        if (quoteContexts == null) {
            timedOut = true
            break
        }

        // The shallowest depth of each line's whole quote block, looked at before the anchors are judged (L85: a running
        // minimum made an anchor direct when a shallower line of the same block came after it).
        val blockDepths = threadQuoteBlockDepths(lines, config)
        val postReferences = mutableListOf<QuoteReference>()
        val referencedTargets = mutableSetOf<String>()
        val pendingContentLines = mutableListOf<String>()
        var pendingContentTargets: Set<String>? = null
        var isContentBlockInvalid = false
        var pendingContentContext: ThreadQuoteSourceContext? = null

        fun flushPendingContentBlock() {
            val targets = pendingContentTargets
            if (pendingContentLines.isNotEmpty() &&
                !isContentBlockInvalid &&
                targets != null &&
                targets.isNotEmpty()
            ) {
                val replyTargets = if (pendingContentContext?.hasRequotedLine == true) {
                    pendingContentContext?.candidates?.singleOrNull()
                        ?.takeIf { it in targets }?.let(::setOf).orEmpty()
                } else targets
                postReferences.add(
                    QuoteReference(
                        text = pendingContentLines.joinToString("\n").trim(),
                        targetPostIds = targets.toList(),
                        isDirectReply = replyTargets == targets,
                        replyTargetPostIds = replyTargets.toList()
                    )
                )
                referencedTargets += replyTargets
            }
            pendingContentLines.clear()
            pendingContentTargets = null
            isContentBlockInvalid = false
            pendingContentContext = null
        }

        var lineIndex = 0
        for (rawLine in lines) {
            lineIndex += 1
            if (lineIndex % 16 == 0 &&
                isThreadReferenceBuildTimedOut(startedAt, config)
            ) {
                timedOut = true
                Logger.w(config.tag, "Reference rebuild timed out while scanning post=${post.id}")
                break
            }
            val trimmedLine = rawLine.trimStart()
            if (trimmedLine.isBlank()) continue
            if (!(trimmedLine.startsWith(">") || trimmedLine.startsWith("＞"))) {
                flushPendingContentBlock()
                continue
            }
            val depth = threadQuoteDepth(trimmedLine)
            val isDirectAnchor = threadQuoteAnchorDepth(trimmedLine, config) <= blockDepths[lineIndex - 1]

            val resolution = resolveThreadQuoteTargets(
                quoteLine = trimmedLine,
                posterIdIndex = posterIdIndex,
                messageLineIndex = messageLineIndex,
                mediaFileIndex = mediaFileIndex,
                knownPostIds = knownPostIds,
                earlierPostIds = earlierPostIds,
                config = config
            )
            if (resolution.isExplicit) {
                flushPendingContentBlock()
                if (resolution.targets.isNotEmpty()) {
                    val content = trimmedLine.trimStart { it == '>' || it == '＞' }.trim()
                    // A bare >>100 is the explicit numeric shortcut, even if a
                    // previous post happened to contain >100. No. anchors retain
                    // the established number-only requote behavior.
                    val copiedFrom = if (depth > 1 && !config.leadingNumberRegex.matches(content)) messageLineIndex[
                        normalizeThreadQuoteText(content, config.whitespaceRegex)
                    ]?.byDepth?.get(depth - 1).orEmpty() else emptySet()
                    val directTargets = if (copiedFrom.isNotEmpty()) {
                        quoteContexts.getOrNull(lineIndex - 1)?.candidates?.singleOrNull()
                            ?.takeIf { it in copiedFrom }?.let(::setOf).orEmpty()
                    } else if (isDirectAnchor) resolution.targets else emptySet()
                    postReferences.add(
                        QuoteReference(
                            text = trimmedLine.trim(),
                            targetPostIds = resolution.targets.toList(),
                            isDirectReply = isDirectAnchor && copiedFrom.isEmpty(),
                            replyTargetPostIds = directTargets.toList()
                        )
                    )
                    referencedTargets += directTargets
                }
                continue
            }

            val context = quoteContexts.getOrNull(lineIndex - 1)
            if (pendingContentContext !== context &&
                (pendingContentContext?.hasRequotedLine == true || context?.hasRequotedLine == true)
            ) flushPendingContentBlock()
            pendingContentContext = context
            if (resolution.targets.isEmpty()) {
                if (pendingContentLines.isNotEmpty()) {
                    pendingContentLines.add(trimmedLine.trim())
                    pendingContentTargets = emptySet()
                    isContentBlockInvalid = true
                } else {
                    flushPendingContentBlock()
                }
                continue
            }

            val currentTargets = pendingContentTargets
            val updatedTargets = when {
                currentTargets == null -> resolution.targets
                else -> currentTargets.intersect(resolution.targets)
            }

            if (updatedTargets.isEmpty()) {
                pendingContentLines.add(trimmedLine.trim())
                pendingContentTargets = emptySet()
                isContentBlockInvalid = true
            } else {
                pendingContentLines.add(trimmedLine.trim())
                pendingContentTargets = updatedTargets
            }
        }
        if (timedOut) break

        flushPendingContentBlock()

        if (postReferences.isNotEmpty()) {
            referencedTargets.forEach { targetId ->
                if (targetId == post.id) return@forEach
                counts[targetId] = counts[targetId]?.plus(1) ?: 1
            }
            references[post.id] = postReferences
        }

        addPosterIdToThreadReferenceIndex(posterIdIndex, post)
        val indexedMessageLines = addMessageLinesToThreadReferenceIndex(
            index = messageLineIndex,
            post = post,
            lines = lines,
            config = config,
            startedAt = startedAt
        )
        if (!indexedMessageLines) {
            timedOut = true
            Logger.w(config.tag, "Reference rebuild timed out while indexing post=${post.id}")
            break
        }
        addMediaToThreadReferenceIndex(mediaFileIndex, post, config.mediaFilenameRegex)
    }

    return ThreadReferenceData(counts, references, timedOut = timedOut)
}

/** Intersect source owners across each contiguous quote block, retaining the original link targets separately. */
private fun buildThreadQuoteSourceContexts(
    lines: List<String>,
    index: Map<String, ThreadLineTargets>,
    config: ThreadReferenceBuildConfig,
    startedAt: TimeMark
): Array<ThreadQuoteSourceContext?>? {
    val contexts = arrayOfNulls<ThreadQuoteSourceContext>(lines.size)
    var start = -1
    var candidates: Set<String>? = null
    var hasRequotedLine = false
    fun finish(end: Int) {
        if (start < 0) return
        val context = ThreadQuoteSourceContext(candidates.orEmpty(), hasRequotedLine)
        for (position in start until end) contexts[position] = context
        start = -1
        candidates = null
        hasRequotedLine = false
    }
    for ((position, rawLine) in lines.withIndex()) {
        if (position % 32 == 0 && isThreadReferenceBuildTimedOut(startedAt, config)) return null
        val line = rawLine.trimStart()
        // The reference builder ignores HTML formatting/blank lines inside a quote block.
        if (line.isBlank()) continue
        val depth = threadQuoteDepth(line)
        if (depth == 0) {
            finish(position)
            continue
        }
        if (start < 0) start = position
        val content = line.trimStart { it == '>' || it == '＞' }.trim()
        val owners = index[normalizeThreadQuoteText(content, config.whitespaceRegex)]
            ?.byDepth?.get(depth - 1).orEmpty()
        if (owners.isNotEmpty()) {
            candidates = candidates?.intersect(owners) ?: owners
            if (depth > 1 && !config.leadingNumberRegex.matches(content)) hasRequotedLine = true
        }
    }
    finish(lines.size)
    return contexts
}

private fun addPosterIdToThreadReferenceIndex(
    index: MutableMap<String, MutableSet<String>>,
    post: Post
) {
    val id = post.posterId?.takeIf { it.isNotBlank() } ?: return
    val normalized = id.trim()
    index.getOrPut(normalized) { mutableSetOf() }.add(post.id)
}

private fun addMessageLinesToThreadReferenceIndex(
    index: MutableMap<String, ThreadLineTargets>,
    post: Post,
    lines: List<String>,
    config: ThreadReferenceBuildConfig,
    startedAt: TimeMark
): Boolean {
    if (post.messageHtml.isBlank()) return true
    for ((lineIndex, rawLine) in lines.withIndex()) {
        if (lineIndex % 32 == 0 && isThreadReferenceBuildTimedOut(startedAt, config)) {
            return false
        }
        val trimmed = rawLine.trim()
        if (trimmed.isBlank()) continue
        val isQuoted = trimmed.startsWith(">") || trimmed.startsWith("＞")
        val withoutMarkers = trimmed.trimStart { it == '>' || it == '＞' }.trim()
        if (withoutMarkers.isBlank()) continue
        val normalized = normalizeThreadQuoteText(withoutMarkers, config.whitespaceRegex)
        if (normalized.isBlank()) continue
        val bucket = index.getOrPut(normalized) { ThreadLineTargets() }
        if (isQuoted) bucket.quoted.add(post.id) else bucket.plain.add(post.id)
        bucket.byDepth.getOrPut(threadQuoteDepth(trimmed)) { mutableSetOf() }.add(post.id)
    }
    return true
}

private fun isThreadReferenceBuildTimedOut(
    startedAt: TimeMark,
    config: ThreadReferenceBuildConfig
): Boolean {
    return startedAt.elapsedNow().inWholeMilliseconds > config.maxReferenceBuildTimeMs
}

private fun resolveThreadQuoteTargets(
    quoteLine: String,
    posterIdIndex: Map<String, Set<String>>,
    messageLineIndex: Map<String, ThreadLineTargets>,
    mediaFileIndex: Map<String, MutableSet<String>>,
    knownPostIds: Set<String>,
    earlierPostIds: Set<String>,
    config: ThreadReferenceBuildConfig
): ThreadQuoteLineResolution {
    val trimmed = quoteLine.trim()
    if (trimmed.isBlank()) return ThreadQuoteLineResolution(emptySet(), isExplicit = false)
    val content = trimmed.trimStart { it == '>' || it == '＞' }.trim()
    if (content.isBlank()) return ThreadQuoteLineResolution(emptySet(), isExplicit = false)
    val mediaTargets = resolveThreadMediaTargets(content, mediaFileIndex, config.mediaFilenameRegex)
    if (mediaTargets.isNotEmpty()) {
        return ThreadQuoteLineResolution(mediaTargets, isExplicit = true)
    }
    // A bare leading number (">100円ショップ", ">3枚目") is only a post reference when
    // that post exists; otherwise the line is quoted text and falls through to text matching.
    val explicitNumber = findThreadNoReference(content, config.noReferenceRegex, knownPostIds)
        ?: config.leadingNumberRegex.find(content)?.groupValues?.getOrNull(1)
            ?.takeIf { it in knownPostIds }
    if (explicitNumber != null) {
        // An explicit unknown number may belong to a missing post in a partial page. A number
        // that is present here but is this post or a later one cannot be its source, however.
        // Counting it produced "1レス" at the tail while extraction correctly found no reply.
        if (explicitNumber in knownPostIds && explicitNumber !in earlierPostIds) {
            return ThreadQuoteLineResolution(emptySet(), isExplicit = true)
        }
        return ThreadQuoteLineResolution(setOf(explicitNumber), isExplicit = true)
    }
    val idMatch = config.idReferenceRegex.find(content)?.value
    if (idMatch != null) {
        val targets = posterIdIndex[idMatch].orEmpty()
        return ThreadQuoteLineResolution(targets, isExplicit = true)
    }
    val normalized = normalizeThreadQuoteText(content, config.whitespaceRegex)
    if (normalized.isBlank()) return ThreadQuoteLineResolution(emptySet(), isExplicit = false)
    val sourceDepth = (threadQuoteDepth(trimmed) - 1).coerceAtLeast(0)
    val targets = messageLineIndex[normalized]?.resolve(sourceDepth)
    if (!targets.isNullOrEmpty()) {
        return ThreadQuoteLineResolution(targets, isExplicit = false)
    }
    val partialTargets = findThreadPartialLineTargets(normalized, messageLineIndex, config, sourceDepth)
    return ThreadQuoteLineResolution(partialTargets, isExplicit = false)
}

/**
 * A "No.123" opening the quote is always a reference. One later in the text
 * (">これNo.123") only when that post exists: ">シャネルNo.5の香り" is quoted
 * text and must fall through to text matching.
 */
private fun findThreadNoReference(
    content: String,
    noReferenceRegex: Regex,
    knownPostIds: Set<String>
): String? {
    for (match in noReferenceRegex.findAll(content).take(MAX_NO_REFERENCE_CANDIDATES)) {
        val number = match.groupValues.getOrNull(1) ?: continue
        if (match.range.first == 0 || number in knownPostIds) return number
    }
    return null
}

private const val MAX_NO_REFERENCE_CANDIDATES = 16

private fun resolveThreadMediaTargets(
    content: String,
    mediaFileIndex: Map<String, MutableSet<String>>,
    mediaFilenameRegex: Regex
): Set<String> {
    if (mediaFileIndex.isEmpty()) return emptySet()
    val targets = mutableSetOf<String>()
    mediaFilenameRegex.findAll(content).take(32).forEach { match ->
        val normalized = match.value.lowercase()
        targets += mediaFileIndex[normalized].orEmpty()
    }
    return targets
}

private fun normalizeThreadQuoteText(value: String, whitespaceRegex: Regex): String {
    return value.replace(whitespaceRegex, " ").trim()
}

private fun findThreadPartialLineTargets(
    normalizedQuote: String,
    index: Map<String, ThreadLineTargets>,
    config: ThreadReferenceBuildConfig,
    sourceDepth: Int
): Set<String> {
    if (normalizedQuote.length < config.minPartialMatchLength) return emptySet()
    if (index.isEmpty()) return emptySet()
    if (index.size > config.maxPartialMatchScanLines) return emptySet()
    val targets = mutableSetOf<String>()
    index.forEach { (line, ids) ->
        if (line.length < config.minPartialMatchLength) return@forEach
        if (line.contains(normalizedQuote) || normalizedQuote.contains(line)) {
            targets += ids.resolve(sourceDepth)
        }
    }
    return targets
}

private fun addMediaToThreadReferenceIndex(
    index: MutableMap<String, MutableSet<String>>,
    post: Post,
    mediaFilenameRegex: Regex
) {
    extractThreadMediaFileName(post.imageUrl, mediaFilenameRegex)?.let { file ->
        index.getOrPut(file) { mutableSetOf() }.add(post.id)
    }
    extractThreadMediaFileName(post.thumbnailUrl, mediaFilenameRegex)?.let { file ->
        index.getOrPut(file) { mutableSetOf() }.add(post.id)
    }
}

private fun extractThreadMediaFileName(url: String?, mediaFilenameRegex: Regex): String? {
    if (url.isNullOrBlank()) return null
    val cleaned = url.substringBefore('?').substringAfterLast('/', "")
    if (cleaned.isBlank()) return null
    val lower = cleaned.lowercase()
    val match = mediaFilenameRegex.matchEntire(lower) ?: return null
    return match.value
}

/** Only leading markers contribute to nesting; full-width quotes behave identically. */
private fun threadQuoteDepth(line: String): Int = line.takeWhile { it == '>' || it == '＞' }.length

/** A 2ch-style ">>123" is a reply anchor of its own, so it counts as depth 1 (L85: it stays a direct reply). */
private fun threadQuoteAnchorDepth(line: String, config: ThreadReferenceBuildConfig): Int {
    val depth = threadQuoteDepth(line)
    return if (depth == 2 && config.leadingNumberRegex.matches(line.substring(depth).trim())) 1 else depth
}

/**
 * For each line, the shallowest anchor depth of the run of quote lines it belongs to (blank lines do not end a run, any
 * other line does); 0 for lines outside a run.
 */
private fun threadQuoteBlockDepths(lines: List<String>, config: ThreadReferenceBuildConfig): IntArray {
    val depths = IntArray(lines.size)
    var start = -1
    var shallowest = Int.MAX_VALUE
    fun close(end: Int) {
        if (start < 0) return
        for (i in start until end) depths[i] = shallowest
        start = -1
        shallowest = Int.MAX_VALUE
    }
    lines.forEachIndexed { index, raw ->
        val line = raw.trimStart()
        when {
            line.isBlank() -> Unit
            line.startsWith(">") || line.startsWith("＞") -> {
                if (start < 0) start = index
                shallowest = minOf(shallowest, threadQuoteAnchorDepth(line, config))
            }
            else -> close(index)
        }
    }
    close(lines.size)
    return depths
}
