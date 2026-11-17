package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.CompatibilityStore
import kotlinx.coroutines.CancellationException

/** Retry against the current row, retaining edits and never recreating a tab closed in the meantime. */
internal suspend fun persistFutachaGoneTab(store: CompatibilityStore, tabKey: String): Result<Boolean> = try {
    Result.success(store.updateTabIfPresent(tabKey) { it.copy(isDead = true) })
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    Result.failure(failure)
}

internal fun futachaGoneTabSaveFailureReason(failure: Throwable): String =
    failure.message?.trim()?.takeIf(String::isNotEmpty)?.take(300) ?: "端末の記録処理に失敗しました"
