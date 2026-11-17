package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.state.SettingsRecoveryNotices
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal const val UNREADABLE_COMPATIBILITY_NOTICE_PATH = "compatibility/unreadable_compatibility_state.notice.txt"
internal const val UNREADABLE_COMPATIBILITY_NOTICE_KEY = "compatibility-payload"
internal const val UNREADABLE_COMPATIBILITY_NOTICE_TITLE = "としあき(仮)のデータを読み込めませんでした"

private val noticeCleanupScope = CoroutineScope(SupervisorJob() + AppDispatchers.io)

internal fun buildUnreadableCompatibilityPayloadNoticeMessage(
    backupFileName: String,
    location: String
): String = buildString {
    append("としあき(仮)モードの保存データ（タブ・履歴・NG・下書き・設定など）が破損していて読み取れなかったため、")
    append("空の状態で開始しました。")
    append("\n\n読み取れなかったデータは削除せず、")
    append(location)
    append("に「")
    append(backupFileName)
    append("」として残してあります。以後の保存で上書きされることはありません。")
}

/**
 * Tells the user that the toshiaki (compatibility) payload could not be read
 * and where the preserved copy is (P4-1), like the settings-file notice
 * (G-19). The message is kept on disk until it is acknowledged, so a payload
 * found unreadable by a background task (or a launch that ended before the
 * user saw it) is still reported: the next real write replaces the unreadable
 * row, after which only this marker remembers that it happened.
 */
internal suspend fun postUnreadableCompatibilityPayloadNotice(
    fileSystem: FileSystem,
    backupPath: String,
    location: String
) {
    val message = buildUnreadableCompatibilityPayloadNoticeMessage(
        backupFileName = backupPath.substringAfterLast('/'),
        location = location
    )
    fileSystem.writeString(UNREADABLE_COMPATIBILITY_NOTICE_PATH, message).onFailure { error ->
        Logger.e("CompatibilityPersistence", "Could not persist the unreadable-data notice", error)
    }
    postNotice(fileSystem, message)
}

/** Re-posts a notice that was not acknowledged before the process ended. */
internal suspend fun restorePendingUnreadableCompatibilityPayloadNotice(fileSystem: FileSystem) {
    if (!fileSystem.exists(UNREADABLE_COMPATIBILITY_NOTICE_PATH)) return
    val message = fileSystem.readString(UNREADABLE_COMPATIBILITY_NOTICE_PATH).getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: run {
            fileSystem.delete(UNREADABLE_COMPATIBILITY_NOTICE_PATH)
            return
        }
    postNotice(fileSystem, message)
}

private fun postNotice(fileSystem: FileSystem, message: String) {
    SettingsRecoveryNotices.post(
        message = message,
        title = UNREADABLE_COMPATIBILITY_NOTICE_TITLE,
        key = UNREADABLE_COMPATIBILITY_NOTICE_KEY
    ) {
        noticeCleanupScope.launch { fileSystem.delete(UNREADABLE_COMPATIBILITY_NOTICE_PATH) }
    }
}
