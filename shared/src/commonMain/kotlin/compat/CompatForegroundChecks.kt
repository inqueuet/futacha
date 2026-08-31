package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.compat.normalizeCompatPostMedia
import com.valoser.futacha.shared.util.hasEpochIntervalElapsed

const val COMPAT_FOREGROUND_TICK_MILLIS = 60_000L
const val COMPAT_THREAD_UPDATE_INTERVAL_MILLIS = 5L * 60_000L
const val COMPAT_THREAD_EXISTENCE_INTERVAL_MILLIS = 15L * 60_000L
const val COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE =
    "compat.background.backgroundThreadUpdateCheckTime"
const val COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE =
    "compat.background.backgroundThreadExistCheckTime"
/**
 * The target's BackgroundThreadExistCheckAsyncTask skips tabs whose last
 * successful thread-body update is newer than 1,800 seconds.
 */
const val COMPAT_THREAD_EXISTENCE_STALE_MILLIS = 30L * 60_000L

enum class CompatForegroundNetworkPolicy {
    ALWAYS,
    WIFI_ONLY,
    NONE
}

data class CompatForegroundCheckPlan(
    val checkUpdates: Boolean,
    val checkExistence: Boolean
) {
    val hasWork: Boolean get() = checkUpdates || checkExistence
}

fun parseCompatForegroundNetworkPolicy(value: String?): CompatForegroundNetworkPolicy =
    when (value?.trim()?.lowercase()) {
        // `usually`/`wifi`/`none` are the values written by both reference APKs.
        // Keep accepting values written by older compatibility builds so the
        // migration does not silently disable an existing user's checks.
        "usually", "always", "常に確認する" -> CompatForegroundNetworkPolicy.ALWAYS
        "wifi", "wi-fi回線のみ" -> CompatForegroundNetworkPolicy.WIFI_ONLY
        else -> CompatForegroundNetworkPolicy.NONE
    }

fun compatForegroundPolicyEnabled(value: String?): Boolean =
    parseCompatForegroundNetworkPolicy(value) != CompatForegroundNetworkPolicy.NONE

/** The reference stores these hidden timestamps as epoch seconds in an Int. */
fun parseCompatForegroundLastCheckEpochMillis(value: String?): Long {
    val parsed = value?.trim()?.toLongOrNull()?.coerceAtLeast(0L) ?: return 0L
    return if (parsed >= 100_000_000_000L) parsed else parsed * 1_000L
}

fun compatForegroundLastCheckStoredValue(epochMillis: Long): String =
    (epochMillis.coerceAtLeast(0L) / 1_000L).toString()

fun planCompatForegroundChecks(
    nowEpochMillis: Long,
    lastUpdateCheckEpochMillis: Long,
    lastExistenceCheckEpochMillis: Long,
    updatePolicy: CompatForegroundNetworkPolicy,
    existencePolicy: CompatForegroundNetworkPolicy,
    isWifiConnected: Boolean
): CompatForegroundCheckPlan {
    fun allowed(policy: CompatForegroundNetworkPolicy): Boolean = when (policy) {
        CompatForegroundNetworkPolicy.ALWAYS -> true
        CompatForegroundNetworkPolicy.WIFI_ONLY -> isWifiConnected
        CompatForegroundNetworkPolicy.NONE -> false
    }
    return CompatForegroundCheckPlan(
        checkUpdates = allowed(updatePolicy) &&
            hasEpochIntervalElapsed(
                nowEpochMillis,
                lastUpdateCheckEpochMillis,
                COMPAT_THREAD_UPDATE_INTERVAL_MILLIS
            ),
        checkExistence = allowed(existencePolicy) &&
            hasEpochIntervalElapsed(
                nowEpochMillis,
                lastExistenceCheckEpochMillis,
                COMPAT_THREAD_EXISTENCE_INTERVAL_MILLIS
            )
    )
}

fun ThreadPage.toCompatThreadSnapshot(tabKey: String, revision: Long): CompatThreadSnapshot =
    CompatThreadSnapshot(
        tabKey = tabKey,
        revision = revision,
        fetchedAtEpochMillis = revision,
        boardTitle = boardTitle,
        expiresAtLabel = expiresAtLabel,
        deletedNotice = deletedNotice,
        isTruncated = isTruncated,
        truncationReason = truncationReason,
        posts = posts.mapIndexed { index, post ->
            normalizeCompatPostMedia(CompatPostSnapshot(
                position = index,
                postNo = post.id,
                author = post.author,
                subject = post.subject,
                mail = post.mail,
                timestamp = post.timestamp,
                posterId = post.posterId,
                messageHtml = post.messageHtml,
                imageUrl = post.imageUrl,
                thumbnailUrl = post.thumbnailUrl,
                saidaneLabel = post.saidaneLabel,
                isDeleted = post.isDeleted,
                isIsolated = post.isIsolated,
                referencedCount = post.referencedCount,
                thumbnailWidth = post.thumbnailWidth,
                thumbnailHeight = post.thumbnailHeight,
                quoteReferences = post.quoteReferences
            ))
        }
    )

/** Futaba catalog reply counts do not include the opening post. */
fun ThreadPage.compatReplyCount(): Int = (posts.size - 1).coerceAtLeast(0)

/** Convert the shared compatibility cache back into the modern thread model. */
fun CompatThreadSnapshot.toThreadPage(threadId: String): ThreadPage = ThreadPage(
    threadId = threadId,
    boardTitle = boardTitle,
    expiresAtLabel = expiresAtLabel,
    deletedNotice = deletedNotice,
    isTruncated = isTruncated,
    truncationReason = truncationReason,
    posts = posts.map { cached ->
        Post(
            id = cached.postNo,
            order = cached.position,
            author = cached.author,
            subject = cached.subject,
            timestamp = cached.timestamp,
            posterId = cached.posterId,
            messageHtml = cached.messageHtml,
            imageUrl = cached.imageUrl,
            thumbnailUrl = cached.thumbnailUrl,
            saidaneLabel = cached.saidaneLabel,
            isDeleted = cached.isDeleted,
            isIsolated = cached.isIsolated,
            referencedCount = cached.referencedCount,
            quoteReferences = cached.quoteReferences,
            mail = cached.mail,
            thumbnailWidth = cached.thumbnailWidth,
            thumbnailHeight = cached.thumbnailHeight
        )
    }
)
