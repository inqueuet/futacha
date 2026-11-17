package com.valoser.futacha.shared.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex

/**
 * Task-specific availability: a task checks only its own provider, so OpenAI moderation never
 * waits for a busy device AI (on Android every device request shares one worker queue).
 */
internal interface AiTaskAvailability {
    suspend fun summaryAvailability(): AiAvailability
    suspend fun moderationAvailability(): AiAvailability
}

/** Resolve each task independently; adding another provider does not couple the two choices. */
internal class RoutedAiService(
    private val summary: OnDeviceAiService,
    private val moderation: OnDeviceAiService,
    override val configurationKey: String
) : OnDeviceAiService, AiTaskAvailability {
    override val externalSummary get() = summary.isExternalService
    override val externalModeration get() = moderation.isExternalService
    override val hybridModeration get() = moderation.hybridModeration
    override val automaticallyHideModeratedPosts get() = moderation.automaticallyHideModeratedPosts
    override val isExternalService get() = externalSummary || externalModeration
    /**
     * One device model runs one request at a time. Shared with the hybrid moderation's device side
     * when both use the same device AI, so summary and moderation queue for it instead of
     * spending their deadlines waiting for each other.
     */
    val localInferenceLock: Mutex = (moderation as? HybridModerationService)
        ?.takeIf { it.usesLocal(summary) }?.localInferenceLock ?: Mutex()
    private val moderationLabel get() = if (externalModeration && !hybridModeration) "OpenAI Moderation" else null

    override suspend fun getAvailability(): AiAvailability = coroutineScope {
        // Probed concurrently: a slow device check must not delay the other provider.
        val moderationProbe = if (summary === moderation) null else async { availabilityOf(moderation) }
        val a = availabilityOf(summary)
        val b = moderationProbe?.await() ?: a
        val moderationLabel = moderationLabel ?: b.providerLabel
        val summaryAvailable = a.isAvailable && a.supportsThreadSummary
        val moderationAvailable = b.isAvailable && b.supportsPostModeration
        AiAvailability(
            isAvailable = summaryAvailable || moderationAvailable,
            supportsThreadSummary = summaryAvailable,
            supportsPostModeration = moderationAvailable,
            unavailableReason = listOfNotNull(
                if (!summaryAvailable) "要約: ${a.unavailableReason ?: "利用できません"}" else null,
                if (!moderationAvailable) "判定: ${b.unavailableReason ?: "利用できません"}" else null
            ).takeIf { it.isNotEmpty() }?.joinToString("\n"),
            providerLabel = "要約: ${a.providerLabel} / 判定: $moderationLabel",
            isDownloadInProgress = a.isDownloadInProgress || b.isDownloadInProgress,
            downloadedBytes = if (a.isDownloadInProgress) a.downloadedBytes else b.downloadedBytes,
            downloadTotalBytes = if (a.isDownloadInProgress) a.downloadTotalBytes else b.downloadTotalBytes,
            isExternalService = isExternalService,
            summaryProviderLabel = a.providerLabel,
            moderationProviderLabel = moderationLabel,
            externalSummary = externalSummary, externalModeration = externalModeration
        )
    }
    override suspend fun summaryAvailability(): AiAvailability {
        val a = availabilityOf(summary)
        val available = a.isAvailable && a.supportsThreadSummary
        return AiAvailability(available, if (available) null else "要約: ${a.unavailableReason ?: "利用できません"}",
            supportsThreadSummary = available, providerLabel = a.providerLabel,
            isDownloadInProgress = a.isDownloadInProgress, downloadedBytes = a.downloadedBytes,
            downloadTotalBytes = a.downloadTotalBytes, isExternalService = isExternalService,
            summaryProviderLabel = a.providerLabel, externalSummary = externalSummary, externalModeration = externalModeration)
    }
    override suspend fun moderationAvailability(): AiAvailability {
        val b = availabilityOf(moderation)
        val available = b.isAvailable && b.supportsPostModeration
        val label = moderationLabel ?: b.providerLabel
        return AiAvailability(available, if (available) null else "判定: ${b.unavailableReason ?: "利用できません"}",
            supportsPostModeration = available, providerLabel = label,
            isDownloadInProgress = b.isDownloadInProgress, downloadedBytes = b.downloadedBytes,
            downloadTotalBytes = b.downloadTotalBytes, isExternalService = isExternalService,
            moderationProviderLabel = label, externalSummary = externalSummary, externalModeration = externalModeration)
    }
    private suspend fun availabilityOf(service: OnDeviceAiService): AiAvailability = try {
        service.getAvailability()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { AiAvailability(false, "AIの利用状況を確認できませんでした。") }
    override suspend fun summarizeThread(input: ThreadSummaryInput) = summary.summarizeThread(input)
    override suspend fun classifyPosts(input: PostModerationInput) = moderation.classifyPosts(input)
    override suspend fun classifyPosts(input: PostModerationInput, onPartialResult: (List<PostModerationResult>) -> Unit) =
        moderation.classifyPosts(input, onPartialResult)
    override fun cancelActiveRequests() { summary.cancelActiveRequests(); if (summary !== moderation) moderation.cancelActiveRequests() }
    override fun close() { summary.close(); if (summary !== moderation) moderation.close() }
}
