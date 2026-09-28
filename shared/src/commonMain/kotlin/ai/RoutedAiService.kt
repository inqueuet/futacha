package com.valoser.futacha.shared.ai

import kotlinx.coroutines.CancellationException

/** Resolve each task independently; adding another provider does not couple the two choices. */
internal class RoutedAiService(
    private val summary: OnDeviceAiService,
    private val moderation: OnDeviceAiService,
    override val configurationKey: String
) : OnDeviceAiService {
    override val externalSummary get() = summary.isExternalService
    override val externalModeration get() = moderation.isExternalService
    override val automaticallyHideModeratedPosts get() = moderation.automaticallyHideModeratedPosts
    override val isExternalService get() = externalSummary || externalModeration
    override suspend fun getAvailability(): AiAvailability {
        val a = availabilityOf(summary)
        val b = if (summary === moderation) a else availabilityOf(moderation)
        val summaryAvailable = a.isAvailable && a.supportsThreadSummary
        val moderationAvailable = b.isAvailable && b.supportsPostModeration
        return AiAvailability(
            isAvailable = summaryAvailable || moderationAvailable,
            supportsThreadSummary = summaryAvailable,
            supportsPostModeration = moderationAvailable,
            unavailableReason = listOfNotNull(
                if (!summaryAvailable) "要約: ${a.unavailableReason ?: "利用できません"}" else null,
                if (!moderationAvailable) "判定: ${b.unavailableReason ?: "利用できません"}" else null
            ).takeIf { it.isNotEmpty() }?.joinToString("\n"),
            providerLabel = "要約: ${a.providerLabel} / 判定: ${if (externalModeration) "OpenAI Moderation" else b.providerLabel}",
            isDownloadInProgress = a.isDownloadInProgress || b.isDownloadInProgress,
            downloadedBytes = if (a.isDownloadInProgress) a.downloadedBytes else b.downloadedBytes,
            downloadTotalBytes = if (a.isDownloadInProgress) a.downloadTotalBytes else b.downloadTotalBytes,
            isExternalService = isExternalService,
            summaryProviderLabel = a.providerLabel,
            moderationProviderLabel = if (externalModeration) "OpenAI Moderation" else b.providerLabel,
            externalSummary = externalSummary, externalModeration = externalModeration
        )
    }
    private suspend fun availabilityOf(service: OnDeviceAiService): AiAvailability = try {
        service.getAvailability()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { AiAvailability(false, "AIの利用状況を確認できませんでした。") }
    override suspend fun summarizeThread(input: ThreadSummaryInput) = summary.summarizeThread(input)
    override suspend fun classifyPosts(input: PostModerationInput) = moderation.classifyPosts(input)
    override fun cancelActiveRequests() { summary.cancelActiveRequests(); if (summary !== moderation) moderation.cancelActiveRequests() }
    override fun close() { summary.close(); if (summary !== moderation) moderation.close() }
}
