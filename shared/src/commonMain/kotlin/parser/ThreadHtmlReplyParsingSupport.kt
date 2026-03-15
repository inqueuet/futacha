package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.ensureActive
import kotlin.time.Clock

internal data class ThreadReplyParsingConfig(
    val tag: String,
    val maxChunkSize: Int,
    val maxParseTimeMs: Long,
    val maxSingleBlockSize: Int,
    val maxIterations: Int,
    val maxPosts: Int,
    val tableRegex: Regex,
    val tableEndRegex: Regex
)

internal data class ThreadReplyParsingResult(
    val posts: List<Post>,
    val isTruncated: Boolean,
    val truncationReason: String?
)

private data class ThreadReplyBlockMatch(
    val block: String,
    val nextSearchStart: Int
)

private sealed interface ThreadReplyBlockScanResult {
    data object End : ThreadReplyBlockScanResult
    data class Found(val match: ThreadReplyBlockMatch) : ThreadReplyBlockScanResult
    data class Error(val reason: String) : ThreadReplyBlockScanResult
}

internal suspend fun parseThreadReplyBlocks(
    repliesHtml: String,
    initialSearchStart: Int,
    config: ThreadReplyParsingConfig,
    parsePostBlock: suspend (String) -> Post?
): ThreadReplyParsingResult {
    val posts = mutableListOf<Post>()
    var isTruncated = false
    var truncationReason: String? = null
    var searchStart = initialSearchStart
    var iterationCount = 0
    var lastSearchStart = -1
    val parseStartTime = Clock.System.now().toEpochMilliseconds()

    while (searchStart < repliesHtml.length &&
        iterationCount < config.maxIterations &&
        posts.size < config.maxPosts
    ) {
        iterationCount++
        kotlinx.coroutines.currentCoroutineContext().ensureActive()

        val elapsed = Clock.System.now().toEpochMilliseconds() - parseStartTime
        if (elapsed > config.maxParseTimeMs) {
            Logger.e(config.tag, "Parse timeout exceeded ($elapsed ms), stopping parse")
            isTruncated = true
            truncationReason = "Parse timeout exceeded (${elapsed}ms > ${config.maxParseTimeMs}ms)"
            break
        }

        if (searchStart == lastSearchStart) {
            Logger.e(config.tag, "Search position stalled at $searchStart, stopping parse")
            isTruncated = true
            truncationReason = "Parse error: search position stalled"
            break
        }
        lastSearchStart = searchStart

        when (val scanResult = scanNextThreadReplyBlock(repliesHtml, searchStart, config)) {
            ThreadReplyBlockScanResult.End -> break
            is ThreadReplyBlockScanResult.Error -> {
                isTruncated = true
                truncationReason = scanResult.reason
                break
            }
            is ThreadReplyBlockScanResult.Found -> {
                val block = scanResult.match.block
                if (block.length > config.maxChunkSize) {
                    Logger.w(config.tag, "Large table block ${block.length} bytes")
                    if (block.length > config.maxSingleBlockSize) {
                        Logger.w(
                            config.tag,
                            "Skipping block exceeding safe size limit (${block.length} > ${config.maxSingleBlockSize})"
                        )
                        isTruncated = true
                        if (truncationReason == null) {
                            truncationReason = "Skipped oversized post block (${block.length} bytes)"
                        }
                        searchStart = scanResult.match.nextSearchStart
                        continue
                    }
                }

                if (block.contains("class=\"cno\"", ignoreCase = true) ||
                    block.contains("class=cno", ignoreCase = true)
                ) {
                    parsePostBlock(block)?.let(posts::add)
                }
                searchStart = scanResult.match.nextSearchStart
            }
        }
    }

    if (iterationCount >= config.maxIterations) {
        Logger.w(config.tag, "Reached maximum iteration limit (${config.maxIterations}), thread may be truncated")
        isTruncated = true
        truncationReason = "Exceeded maximum iteration limit (${config.maxIterations})"
    }
    if (posts.size >= config.maxPosts) {
        Logger.w(config.tag, "Reached maximum post limit (${config.maxPosts}), thread truncated")
        isTruncated = true
        truncationReason = "Thread has more than ${config.maxPosts} posts"
    }

    return ThreadReplyParsingResult(
        posts = posts,
        isTruncated = isTruncated,
        truncationReason = truncationReason
    )
}

private fun scanNextThreadReplyBlock(
    repliesHtml: String,
    searchStart: Int,
    config: ThreadReplyParsingConfig
): ThreadReplyBlockScanResult {
    val tableStart = try {
        config.tableRegex.find(repliesHtml, searchStart)
    } catch (e: Exception) {
        Logger.e(config.tag, "Regex exception during table start search", e)
        return ThreadReplyBlockScanResult.Error("Parse error: regex exception")
    } ?: return ThreadReplyBlockScanResult.End

    val tableEnd = try {
        config.tableEndRegex.find(repliesHtml, tableStart.range.last)
    } catch (e: Exception) {
        Logger.e(config.tag, "Regex exception during table end search", e)
        return ThreadReplyBlockScanResult.Error("Parse error: regex exception")
    } ?: return ThreadReplyBlockScanResult.End

    if (tableEnd.range.last <= tableStart.range.last) {
        Logger.e(config.tag, "Invalid table range detected, stopping parse")
        return ThreadReplyBlockScanResult.Error("Parse error: invalid table range")
    }
    if (tableEnd.range.last >= Int.MAX_VALUE - 1) {
        Logger.e(config.tag, "Range end position near Int.MAX_VALUE, cannot continue")
        return ThreadReplyBlockScanResult.Error("Parse error: range overflow")
    }

    val blockEndExclusive = tableEnd.range.last + 1
    if (blockEndExclusive > repliesHtml.length ||
        tableStart.range.first < 0 ||
        tableStart.range.first >= blockEndExclusive
    ) {
        Logger.e(
            config.tag,
            "Invalid block bounds detected (start=${tableStart.range.first}, endExclusive=$blockEndExclusive, length=${repliesHtml.length})"
        )
        return ThreadReplyBlockScanResult.Error("Parse error: invalid block bounds")
    }

    if (blockEndExclusive <= searchStart) {
        Logger.e(
            config.tag,
            "Search position not advancing properly (old=$searchStart, new=$blockEndExclusive), stopping parse"
        )
        return ThreadReplyBlockScanResult.Error("Parse error: search position not advancing")
    }

    return ThreadReplyBlockScanResult.Found(
        ThreadReplyBlockMatch(
            block = repliesHtml.substring(tableStart.range.first, blockEndExclusive),
            nextSearchStart = blockEndExclusive
        )
    )
}
