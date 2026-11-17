package com.valoser.futacha.shared.ai

/** Appended to the provider label of a summary extracted from the posts because the model failed. */
internal const val THREAD_SUMMARY_FALLBACK_LABEL_SUFFIX = "（簡易要約）"

/**
 * Extract-only summary shown when the device model could not summarise. It is labelled as such,
 * and callers do not cache it as the thread's summary, so a later attempt can regenerate it.
 */
internal fun buildFallbackThreadSummary(input: ThreadSummaryInput, providerLabel: String): ThreadSummary =
    buildExtractiveThreadSummary(input, providerLabel = providerLabel + THREAD_SUMMARY_FALLBACK_LABEL_SUFFIX)

internal val ThreadSummary.isFallbackThreadSummary: Boolean
    get() = providerLabel.endsWith(THREAD_SUMMARY_FALLBACK_LABEL_SUFFIX)
