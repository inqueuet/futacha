package com.valoser.futacha.shared.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A message shown once after a damaged settings file was replaced by what
 * could be recovered from it. The platform storage keeps the message on disk
 * until it is acknowledged, so a recovery that happened in a background
 * worker is still reported the next time the app is opened.
 */
object SettingsRecoveryNotices {
    /** The notice currently shown: [title] and [message]. */
    data class Notice(val title: String, val message: String)

    private class Pending(val key: String, val notice: Notice, val onAcknowledged: () -> Unit)

    // Notices from different sources (settings file, toshiaki saved data) are
    // queued instead of replacing each other; one source keeps one notice.
    private val pendingState = MutableStateFlow<List<Pending>>(emptyList())
    private val noticeState = MutableStateFlow<Notice?>(null)
    private val messageState = MutableStateFlow<String?>(null)

    val notice: StateFlow<Notice?> = noticeState.asStateFlow()
    val message: StateFlow<String?> = messageState.asStateFlow()

    internal fun post(
        message: String,
        title: String = SETTINGS_RECOVERY_NOTICE_TITLE,
        key: String = SETTINGS_RECOVERY_NOTICE_KEY,
        onAcknowledged: () -> Unit
    ) {
        val pending = Pending(key, Notice(title, message), onAcknowledged)
        val current = pendingState.value
        val index = current.indexOfFirst { it.key == key }
        update(if (index >= 0) current.toMutableList().also { it[index] = pending } else current + pending)
    }

    /** Acknowledges the notice on screen; the next queued one (if any) follows. */
    fun acknowledge() {
        val current = pendingState.value
        val pending = current.firstOrNull() ?: return
        update(current.drop(1))
        runCatching(pending.onAcknowledged)
    }

    private fun update(pending: List<Pending>) {
        pendingState.value = pending
        noticeState.value = pending.firstOrNull()?.notice
        messageState.value = pending.firstOrNull()?.notice?.message
    }
}

internal const val SETTINGS_RECOVERY_NOTICE_TITLE = "設定を復元しました"
internal const val SETTINGS_RECOVERY_NOTICE_KEY = "settings"

internal fun buildSettingsRecoveryNoticeMessage(
    recoveredEntryCount: Int,
    appLockPasswordLost: Boolean,
    backupFileName: String?
): String = buildString {
    if (recoveredEntryCount > 0) {
        append("設定ファイルが破損していたため、読み取れた設定だけを復元しました。")
        append("板・NGなどの一部の設定が初期状態に戻っている可能性があります。")
    } else {
        append("設定ファイルが破損していて読み取れなかったため、設定を初期状態に戻しました。")
    }
    if (appLockPasswordLost) {
        append("\n\nアプリロックのパスワードを復元できなかったため、アプリロックは解除されています。")
        append("必要な場合は設定から再度設定してください。")
    }
    if (backupFileName != null) {
        append("\n\n破損したファイルは設定の保存場所に「")
        append(backupFileName)
        append("」として残してあります。")
    }
}
