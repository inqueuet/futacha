package com.valoser.futacha.shared.compat

internal const val DEFAULT_COMPAT_CLOSE_TOAST_DURATION_MILLIS = 7_000L
internal const val COMPAT_AUTO_SCROLL_TOUCH_PAUSE_MILLIS = 5_000L
internal const val COMPAT_AUTO_SCROLL_RELOAD_WAIT_MILLIS = 12_000L

internal enum class CompatAutoScrollAction { SCROLL, WAIT_FOR_RELOAD, STOP_DEAD }

internal fun resolveCompatAutoScrollAction(
    canScrollForward: Boolean,
    isDead: Boolean
): CompatAutoScrollAction = when {
    isDead && !canScrollForward -> CompatAutoScrollAction.STOP_DEAD
    !canScrollForward -> CompatAutoScrollAction.WAIT_FOR_RELOAD
    else -> CompatAutoScrollAction.SCROLL
}

internal fun resolveCompatCloseToastDurationMillis(rawValue: String?): Long {
    return rawValue
        ?.filter(Char::isDigit)
        ?.toLongOrNull()
        ?.coerceIn(0L, DEFAULT_COMPAT_CLOSE_TOAST_DURATION_MILLIS)
        ?: DEFAULT_COMPAT_CLOSE_TOAST_DURATION_MILLIS
}

/** The reference APK only announces a batch close; a single close stays quiet. */
internal fun shouldShowCompatCloseToast(closedTabCount: Int, durationMillis: Long): Boolean =
    closedTabCount > 1 && durationMillis > 0L

/**
 * The legacy thread screen keeps the response count from before a manual
 * refresh and places an attention row at the first newly fetched response.
 */
data class CompatNewReplyNotice(
    val count: Int,
    val firstNewPostPosition: Int
)

sealed interface CompatManualRefreshNotice {
    data class NewReplies(val notice: CompatNewReplyNotice) : CompatManualRefreshNotice
    data object NoNewReplies : CompatManualRefreshNotice
}

data class CompatThreadUpdateNotices(
    val newReply: CompatNewReplyNotice? = null,
    val manualRefresh: CompatManualRefreshNotice? = null
)

data class CompatThreadStatusFlags(
    val isDeleted: Boolean = false,
    val isIsolated: Boolean = false,
    val isAdminDeleted: Boolean = false
)

/**
 * The reference APK stores these three states separately from "落ち".  HTML
 * responses expose the information in the deletion notice, so do not collapse
 * every non-200 result into the dead flag.
 */
fun parseCompatThreadStatusFlags(deletedNotice: String?): CompatThreadStatusFlags {
    val notice = deletedNotice.orEmpty().replace(" ", "")
    if (notice.isBlank()) return CompatThreadStatusFlags()
    val isolated = notice.contains("隔離")
    val adminDeleted = notice.contains("管理者") && notice.contains("削除")
    val deleted = !isolated && !adminDeleted && notice.contains("削除")
    return CompatThreadStatusFlags(
        isDeleted = deleted,
        isIsolated = isolated,
        isAdminDeleted = adminDeleted
    )
}

fun detectCompatNewReplyNotice(
    previous: CompatThreadSnapshot?,
    fetched: CompatThreadSnapshot
): CompatNewReplyNotice? {
    if (previous == null || previous.posts.isEmpty()) return null
    if (fetched.posts.size <= previous.posts.size) return null

    val previousPostIds = previous.posts.map(CompatPostSnapshot::postNo)
    val fetchedPostIds = fetched.posts.map(CompatPostSnapshot::postNo)
    if (fetchedPostIds.take(previousPostIds.size) != previousPostIds) return null

    return CompatNewReplyNotice(
        count = fetched.posts.size - previous.posts.size,
        firstNewPostPosition = previous.posts.size
    )
}

/** A manual refresh reports both the append-only and unchanged outcomes. */
fun detectCompatManualRefreshNotice(
    previous: CompatThreadSnapshot?,
    fetched: CompatThreadSnapshot
): CompatManualRefreshNotice? {
    if (previous == null || previous.posts.isEmpty()) return null
    detectCompatNewReplyNotice(previous, fetched)?.let {
        return CompatManualRefreshNotice.NewReplies(it)
    }
    val previousPostIds = previous.posts.map(CompatPostSnapshot::postNo)
    val fetchedPostIds = fetched.posts.map(CompatPostSnapshot::postNo)
    return CompatManualRefreshNotice.NoNewReplies.takeIf { fetchedPostIds == previousPostIds }
}

fun resolveCompatThreadUpdateNotices(
    previous: CompatThreadSnapshot?,
    fetched: CompatThreadSnapshot,
    manual: Boolean,
    committed: Boolean,
    primaryThreadGone: Boolean
): CompatThreadUpdateNotices {
    // Replies supplied by an archive are not live new replies. More
    // importantly, showing their count together with the dropped-thread
    // message produces two conflicting toasts for the same refresh.
    if (!committed || primaryThreadGone) return CompatThreadUpdateNotices()
    val newReply = detectCompatNewReplyNotice(previous, fetched)
    return CompatThreadUpdateNotices(
        newReply = newReply,
        manualRefresh = if (manual) detectCompatManualRefreshNotice(previous, fetched) else null
    )
}

fun CompatManualRefreshNotice.message(): String = when (this) {
    is CompatManualRefreshNotice.NewReplies -> "新着レス${notice.count}件"
    CompatManualRefreshNotice.NoNewReplies -> "新着なし"
}

fun compatThreadFooterLabel(
    snapshot: CompatThreadSnapshot?,
    isDead: Boolean
): String? {
    val expiresAtLabel = snapshot?.expiresAtLabel
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    if (expiresAtLabel != null) {
        return if (expiresAtLabel.startsWith("消滅：")) {
            expiresAtLabel
        } else {
            "消滅：$expiresAtLabel"
        }
    }
    return "スレッドは落ちました".takeIf { isDead }
}
