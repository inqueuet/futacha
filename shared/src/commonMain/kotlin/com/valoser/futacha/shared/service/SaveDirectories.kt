package com.valoser.futacha.shared.service

// 手動保存は共有Documents/futacha配下に置き、ユーザーが参照できるようにする
const val MANUAL_SAVE_DIRECTORY = "saved_threads"
const val DEFAULT_MANUAL_SAVE_ROOT = "Documents"

// 自動保存は FileSystem 側でアプリ専用の非公開ディレクトリへ解決される
const val AUTO_SAVE_DIRECTORY = "autosaved_threads"

private const val STORAGE_KEY_DELIMITER = "__"
private val INVALID_STORAGE_SEGMENT_REGEX = Regex("""[^A-Za-z0-9._-]""")

fun buildThreadStorageId(boardId: String?, threadId: String): String {
    val safeThread = sanitizeStorageSegment(threadId).ifBlank { "thread" }
    val safeBoard = sanitizeStorageSegment(boardId.orEmpty())
    return if (safeBoard.isBlank()) {
        safeThread
    } else {
        "$safeBoard$STORAGE_KEY_DELIMITER$safeThread"
    }
}

private fun sanitizeStorageSegment(value: String): String {
    return value
        .trim()
        .replace(INVALID_STORAGE_SEGMENT_REGEX, "_")
        .trim('_')
        .take(80)
}
