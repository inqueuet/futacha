package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.util.Logger
import kotlin.time.Clock

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

private data class ThreadLineTargets(
    val plain: MutableSet<String> = mutableSetOf(),
    val quoted: MutableSet<String> = mutableSetOf()
) {
    fun resolve(): Set<String> = if (plain.isNotEmpty()) plain else quoted
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
    val startedAtMillis = Clock.System.now().toEpochMilliseconds()
    var timedOut = false
    val decodedMessages = mutableMapOf<String, List<String>>()

    for ((index, post) in posts.withIndex()) {
        if (index % 32 == 0) {
            val elapsed = Clock.System.now().toEpochMilliseconds() - startedAtMillis
            if (elapsed > config.maxReferenceBuildTimeMs) {
                timedOut = true
                Logger.w(config.tag, "Reference rebuild timed out at post=$index elapsed=${elapsed}ms")
                break
            }
        }
        if (post.messageHtml.isBlank()) continue

        val lines = decodedMessages.getOrPut(post.id) {
            decodeHtmlEntities(stripTags(post.messageHtml))
                .lines()
                .map { it.trimStart() }
        }
        if (lines.isEmpty()) continue

        val postReferences = mutableListOf<QuoteReference>()
        val referencedTargets = mutableSetOf<String>()
        val pendingContentLines = mutableListOf<String>()
        var pendingContentTargets: Set<String>? = null
        var isContentBlockInvalid = false

        fun flushPendingContentBlock() {
            val targets = pendingContentTargets
            if (pendingContentLines.isNotEmpty() &&
                !isContentBlockInvalid &&
                targets != null &&
                targets.isNotEmpty()
            ) {
                postReferences.add(
                    QuoteReference(
                        text = pendingContentLines.joinToString("\n").trim(),
                        targetPostIds = targets.toList()
                    )
                )
                referencedTargets += targets
            }
            pendingContentLines.clear()
            pendingContentTargets = null
            isContentBlockInvalid = false
        }

        lines.forEach { rawLine ->
            val trimmedLine = rawLine.trimStart()
            if (trimmedLine.isBlank()) return@forEach
            if (!(trimmedLine.startsWith(">") || trimmedLine.startsWith("＞"))) {
                flushPendingContentBlock()
                return@forEach
            }

            val resolution = resolveThreadQuoteTargets(
                quoteLine = trimmedLine,
                posterIdIndex = posterIdIndex,
                messageLineIndex = messageLineIndex,
                mediaFileIndex = mediaFileIndex,
                config = config
            )
            if (resolution.isExplicit) {
                flushPendingContentBlock()
                if (resolution.targets.isNotEmpty()) {
                    postReferences.add(
                        QuoteReference(
                            text = trimmedLine.trim(),
                            targetPostIds = resolution.targets.toList()
                        )
                    )
                    referencedTargets += resolution.targets
                }
                return@forEach
            }

            if (resolution.targets.isEmpty()) {
                if (pendingContentLines.isNotEmpty()) {
                    pendingContentLines.add(trimmedLine.trim())
                    pendingContentTargets = emptySet()
                    isContentBlockInvalid = true
                } else {
                    flushPendingContentBlock()
                }
                return@forEach
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

        flushPendingContentBlock()

        if (postReferences.isNotEmpty()) {
            referencedTargets.forEach { targetId ->
                if (targetId == post.id) return@forEach
                counts[targetId] = counts[targetId]?.plus(1) ?: 1
            }
            references[post.id] = postReferences
        }

        addPosterIdToThreadReferenceIndex(posterIdIndex, post)
        addMessageLinesToThreadReferenceIndex(messageLineIndex, post, decodeHtmlEntities, stripTags, config)
        addMediaToThreadReferenceIndex(mediaFileIndex, post, config.mediaFilenameRegex)
    }

    return ThreadReferenceData(counts, references, timedOut = timedOut)
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
    decodeHtmlEntities: (String) -> String,
    stripTags: (String) -> String,
    config: ThreadReferenceBuildConfig
) {
    if (post.messageHtml.isBlank()) return
    decodeHtmlEntities(stripTags(post.messageHtml))
        .lines()
        .forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) return@forEach
            val isQuoted = trimmed.startsWith(">") || trimmed.startsWith("＞")
            val withoutMarkers = trimmed.trimStart { it == '>' || it == '＞' }.trim()
            if (withoutMarkers.isBlank()) return@forEach
            val normalized = normalizeThreadQuoteText(withoutMarkers, config.whitespaceRegex)
            if (normalized.isBlank()) return@forEach
            val bucket = index.getOrPut(normalized) { ThreadLineTargets() }
            if (isQuoted) bucket.quoted.add(post.id) else bucket.plain.add(post.id)
        }
}

private fun resolveThreadQuoteTargets(
    quoteLine: String,
    posterIdIndex: Map<String, Set<String>>,
    messageLineIndex: Map<String, ThreadLineTargets>,
    mediaFileIndex: Map<String, MutableSet<String>>,
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
    val explicitNumber = config.noReferenceRegex.find(content)?.groupValues?.getOrNull(1)
        ?: config.leadingNumberRegex.find(content)?.groupValues?.getOrNull(1)
    if (explicitNumber != null) {
        return ThreadQuoteLineResolution(setOf(explicitNumber), isExplicit = true)
    }
    val idMatch = config.idReferenceRegex.find(content)?.value
    if (idMatch != null) {
        val targets = posterIdIndex[idMatch].orEmpty()
        return ThreadQuoteLineResolution(targets, isExplicit = true)
    }
    val normalized = normalizeThreadQuoteText(content, config.whitespaceRegex)
    if (normalized.isBlank()) return ThreadQuoteLineResolution(emptySet(), isExplicit = false)
    val targets = messageLineIndex[normalized]?.resolve()
    if (!targets.isNullOrEmpty()) {
        return ThreadQuoteLineResolution(targets, isExplicit = false)
    }
    val partialTargets = findThreadPartialLineTargets(normalized, messageLineIndex, config)
    return ThreadQuoteLineResolution(partialTargets, isExplicit = false)
}

private fun resolveThreadMediaTargets(
    content: String,
    mediaFileIndex: Map<String, MutableSet<String>>,
    mediaFilenameRegex: Regex
): Set<String> {
    if (mediaFileIndex.isEmpty()) return emptySet()
    val matches = mediaFilenameRegex.findAll(content)
    if (!matches.iterator().hasNext()) return emptySet()
    val targets = mutableSetOf<String>()
    matches.forEach { match ->
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
    config: ThreadReferenceBuildConfig
): Set<String> {
    if (normalizedQuote.length < config.minPartialMatchLength) return emptySet()
    if (index.isEmpty()) return emptySet()
    if (index.size > config.maxPartialMatchScanLines) return emptySet()
    val targets = mutableSetOf<String>()
    index.forEach { (line, ids) ->
        if (line.length < config.minPartialMatchLength) return@forEach
        if (line.contains(normalizedQuote) || normalizedQuote.contains(line)) {
            targets += ids.resolve()
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
