package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.valoser.futacha.shared.network.HttpBoardApiPostingFailureKind
import com.valoser.futacha.shared.network.classifyHttpBoardApiPostingFailure

private val postingFailureEquivalentPrefixes = mapOf(
    "返信の送信に失敗しました" to listOf("返信に失敗しました"),
    "スレッド作成に失敗しました" to listOf("スレッド作成に失敗しました")
)
internal const val POSTING_FAILURE_COOKIE_ACTION_LABEL = "Cookie"

internal fun buildPostingAwareFailureMessage(
    failurePrefix: String,
    error: Throwable,
    fallbackDetail: String? = null
): String {
    val detail = error.message?.trim().takeUnless { it.isNullOrEmpty() } ?: fallbackDetail.orEmpty()
    if (detail.isEmpty()) {
        return failurePrefix
    }
    if (detail.startsWith(failurePrefix)) {
        return detail
    }
    val equivalentPrefixes = postingFailureEquivalentPrefixes[failurePrefix].orEmpty()
    if (equivalentPrefixes.any { detail.startsWith(it) }) {
        return detail
    }
    return "$failurePrefix: $detail"
}

internal fun shouldOfferCookieManagerForPostingFailure(error: Throwable): Boolean {
    val detail = error.message?.trim().orEmpty()
    if (detail.isEmpty()) {
        return false
    }
    return when (classifyHttpBoardApiPostingFailure(detail)) {
        HttpBoardApiPostingFailureKind.COOKIE_RESET_REQUIRED -> true
        else -> false
    }
}

internal suspend fun showPostingFailureSnackbar(
    snackbarHostState: SnackbarHostState,
    message: String,
    error: Throwable,
    onOpenCookieManager: (() -> Unit)?
) {
    if (onOpenCookieManager == null || !shouldOfferCookieManagerForPostingFailure(error)) {
        snackbarHostState.showSnackbar(message)
        return
    }
    val result = snackbarHostState.showSnackbar(
        message = message,
        actionLabel = POSTING_FAILURE_COOKIE_ACTION_LABEL,
        withDismissAction = true,
        duration = SnackbarDuration.Long
    )
    if (result == SnackbarResult.ActionPerformed) {
        onOpenCookieManager()
    }
}
