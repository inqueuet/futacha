package com.valoser.futacha.wear

internal const val PHONE_UNSUPPORTED_NOTICE =
    "スマホがふたちゃモードではないため操作できません（ふたちゃモードでのみ対応）"
internal const val NOTIFICATIONS_BLOCKED_NOTICE =
    "通知がオフです。設定→アプリ→futacha→通知で許可すると監視アラートが届きます"

/**
 * The status text under the connection label. When the phone is not in the ふたちゃ mode it drops
 * every command, so the optimistic "要求しました" message is replaced by the reason. A missing
 * notification permission is appended: the request is made only once, and without it the
 * watch-word alerts and the read-aloud Live Update never appear.
 */
internal fun buildWatchStatusNotice(
    statusMessage: String?,
    isPhoneUnsupported: Boolean,
    areNotificationsBlocked: Boolean
): String? {
    val parts = buildList {
        if (isPhoneUnsupported) {
            add(PHONE_UNSUPPORTED_NOTICE)
        } else if (statusMessage != null) {
            add(statusMessage)
        }
        if (areNotificationsBlocked) add(NOTIFICATIONS_BLOCKED_NOTICE)
    }
    return parts.joinToString("\n").ifEmpty { null }
}
